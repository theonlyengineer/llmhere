#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <android/log.h>
#include "llama.h"

#define TAG "EdgeLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// Route llama.cpp / ggml logs to Android logcat
static void llama_log_callback(ggml_log_level level, const char *text, void * /*user_data*/) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:
            LOGE("%s", text);
            break;
        case GGML_LOG_LEVEL_WARN:
            LOGW("%s", text);
            break;
        case GGML_LOG_LEVEL_INFO:
            LOGI("%s", text);
            break;
        default:
            LOGD("%s", text);
            break;
    }
}

struct EngineContext {
    llama_model   *model   = nullptr;
    llama_context *ctx     = nullptr;
    llama_sampler *sampler = nullptr;

    std::atomic<bool> cancelled{false};

    // Generation state
    std::vector<llama_token> prompt_tokens;
    int n_decoded = 0;
    int max_tokens = 0;
};

// Helper: convert llama_token to UTF-8 string
static std::string token_to_string(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n < 0) {
        // Buffer too small, allocate dynamically
        std::vector<char> big_buf(-n);
        n = llama_token_to_piece(vocab, token, big_buf.data(), big_buf.size(), 0, true);
        if (n > 0) {
            return std::string(big_buf.data(), n);
        }
        return "";
    }
    return std::string(buf, n);
}

extern "C" {

// ── loadModel ──────────────────────────────────────────────────────────
JNIEXPORT jlong JNICALL
Java_dev_edgellm_engine_llamacpp_NativeBindingsImpl_loadModel(
        JNIEnv *env, jobject /* this */,
        jstring jpath, jint contextSize, jint gpuLayers, jboolean useMmap,
        jint threads, jint threadsBatch, jboolean flashAttention, jint kvCacheType) {

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) {
        LOGE("loadModel: failed to get path string");
        return 0L;
    }

    LOGI("loadModel: loading %s (ctx=%d, gpu=%d, mmap=%d, threads=%d, threadsBatch=%d, flash=%d, kvCache=%d)",
         path, contextSize, gpuLayers, useMmap, threads, threadsBatch, flashAttention, kvCacheType);

    // Initialize backend (idempotent) and route logs to logcat
    llama_log_set(llama_log_callback, nullptr);
    llama_backend_init();

    // Model params
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = useMmap;
    model_params.n_gpu_layers = gpuLayers;

    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGE("loadModel: failed to load model");
        return 0L;
    }

    // Context params
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx           = contextSize;
    ctx_params.n_batch         = contextSize;
    ctx_params.n_threads       = threads;
    ctx_params.n_threads_batch = threadsBatch;
    ctx_params.flash_attn_type = flashAttention
        ? LLAMA_FLASH_ATTN_TYPE_ENABLED : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    ctx_params.type_k          = static_cast<ggml_type>(kvCacheType);
    ctx_params.type_v          = static_cast<ggml_type>(kvCacheType);

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("loadModel: failed to create context");
        llama_model_free(model);
        return 0L;
    }

    auto *engine = new EngineContext();
    engine->model = model;
    engine->ctx = ctx;

    LOGI("loadModel: success, handle=%p", static_cast<void *>(engine));
    return reinterpret_cast<jlong>(engine);
}

// ── unloadModel ────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_dev_edgellm_engine_llamacpp_NativeBindingsImpl_unloadModel(
        JNIEnv * /* env */, jobject /* this */, jlong handle) {

    auto *engine = reinterpret_cast<EngineContext *>(handle);
    if (!engine) return;

    LOGI("unloadModel: handle=%p", static_cast<void *>(engine));

    if (engine->sampler) {
        llama_sampler_free(engine->sampler);
        engine->sampler = nullptr;
    }
    if (engine->ctx) {
        llama_free(engine->ctx);
        engine->ctx = nullptr;
    }
    if (engine->model) {
        llama_model_free(engine->model);
        engine->model = nullptr;
    }

    delete engine;
}

// ── startGeneration ────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_dev_edgellm_engine_llamacpp_NativeBindingsImpl_startGeneration(
        JNIEnv *env, jobject /* this */,
        jlong handle, jstring jprompt,
        jint maxTokens, jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty) {

    auto *engine = reinterpret_cast<EngineContext *>(handle);
    if (!engine || !engine->ctx) return;

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt) return;

    engine->cancelled.store(false);
    engine->n_decoded = 0;
    engine->max_tokens = maxTokens;

    // Free previous sampler if any
    if (engine->sampler) {
        llama_sampler_free(engine->sampler);
        engine->sampler = nullptr;
    }

    // Tokenize prompt
    const llama_vocab *vocab = llama_model_get_vocab(engine->model);
    bool add_bos = llama_vocab_get_add_bos(vocab);

    int n_prompt_max = strlen(prompt) + 128;
    engine->prompt_tokens.resize(n_prompt_max);
    int n_tokens = llama_tokenize(
        vocab,
        prompt, strlen(prompt),
        engine->prompt_tokens.data(), n_prompt_max,
        add_bos, true
    );
    env->ReleaseStringUTFChars(jprompt, prompt);

    if (n_tokens < 0) {
        LOGE("startGeneration: tokenization failed");
        return;
    }
    engine->prompt_tokens.resize(n_tokens);

    LOGI("startGeneration: %d prompt tokens, maxTokens=%d", n_tokens, maxTokens);

    // Create sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    engine->sampler = llama_sampler_chain_init(sparams);

    // Add samplers in order: penalties → top-k → top-p → temperature → dist
    if (repeatPenalty != 1.0f) {
        llama_sampler_chain_add(engine->sampler,
            llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
    }
    llama_sampler_chain_add(engine->sampler, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(engine->sampler, llama_sampler_init_top_p(topP, 1));
    if (temperature > 0.0f) {
        llama_sampler_chain_add(engine->sampler, llama_sampler_init_temp(temperature));
    }
    llama_sampler_chain_add(engine->sampler, llama_sampler_init_dist(42));

    // Clear the KV cache so each generation starts from a clean context. The full
    // prompt (including any prior turns the session chose to include) is decoded
    // fresh below. Without this, the previous conversation's tokens remain resident
    // in the context and a new chat would continue the old one.
    llama_memory_clear(llama_get_memory(engine->ctx), true);

    // Decode prompt
    llama_batch batch = llama_batch_get_one(
        engine->prompt_tokens.data(), engine->prompt_tokens.size());
    if (llama_decode(engine->ctx, batch) != 0) {
        LOGE("startGeneration: prompt decode failed");
        return;
    }
}

// ── nextToken ──────────────────────────────────────────────────────────
JNIEXPORT jstring JNICALL
Java_dev_edgellm_engine_llamacpp_NativeBindingsImpl_nextToken(
        JNIEnv *env, jobject /* this */, jlong handle) {

    auto *engine = reinterpret_cast<EngineContext *>(handle);
    if (!engine || !engine->ctx || !engine->sampler) return nullptr;

    if (engine->cancelled.load()) return nullptr;
    if (engine->n_decoded >= engine->max_tokens) return nullptr;

    // Sample next token
    llama_token new_token = llama_sampler_sample(engine->sampler, engine->ctx, -1);

    // Check for end of generation
    const llama_vocab *vocab = llama_model_get_vocab(engine->model);
    if (llama_vocab_is_eog(vocab, new_token)) {
        return nullptr;
    }

    // Convert to text
    std::string piece = token_to_string(vocab, new_token);

    // Prepare for next decode
    llama_batch batch = llama_batch_get_one(&new_token, 1);
    if (llama_decode(engine->ctx, batch) != 0) {
        LOGE("nextToken: decode failed at token %d", engine->n_decoded);
        return nullptr;
    }

    engine->n_decoded++;

    return env->NewStringUTF(piece.c_str());
}

// ── cancelGeneration ───────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_dev_edgellm_engine_llamacpp_NativeBindingsImpl_cancelGeneration(
        JNIEnv * /* env */, jobject /* this */, jlong handle) {

    auto *engine = reinterpret_cast<EngineContext *>(handle);
    if (!engine) return;

    LOGI("cancelGeneration");
    engine->cancelled.store(true);
}

// ── backendVersion ─────────────────────────────────────────────────────
JNIEXPORT jstring JNICALL
Java_dev_edgellm_engine_llamacpp_NativeBindingsImpl_backendVersion(
        JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF("llama.cpp");
}

} // extern "C"
