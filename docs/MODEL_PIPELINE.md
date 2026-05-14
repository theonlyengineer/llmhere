# Model Pipeline

How model files are prepared offline and delivered to the app at runtime.
The app never trains or quantizes anything; all model preparation is done
on a workstation and the resulting GGUF files plus metadata flow through
the catalog.

## End-to-end pipeline

```
HuggingFace model (HF format, fp16/bf16 weights)
    │
    │  llama.cpp's convert_hf_to_gguf.py
    ▼
GGUF (fp16) — large, accurate
    │
    │  llama.cpp's llama-quantize binary
    ▼
GGUF (Q4_K_M / Q5_K_M / Q8_0 / ...)
    │
    │  upload to host (HuggingFace, S3, your CDN)
    ▼
URL + sha256 + metadata
    │
    │  add entry to catalog.json
    ▼
App reads catalog → user picks a model →
WorkManager downloads → SHA256 verify →
file lands in app's internal storage →
InferenceEngine.load() consumes it
```

## Preparing a model (offline, on workstation)

### Prerequisites (workstation)
```bash
# Clone llama.cpp once (a copy separate from the in-repo submodule)
git clone https://github.com/ggerganov/llama.cpp ~/tools/llama.cpp
cd ~/tools/llama.cpp
make -j                       # builds llama-quantize and friends
pip install -r requirements.txt
```

### Convert + quantize

```bash
# 1. Download the HF model
huggingface-cli download google/gemma-4-e2b-it --local-dir ~/models/gemma-4-e2b-it

# 2. Convert to fp16 GGUF
python convert_hf_to_gguf.py ~/models/gemma-4-e2b-it \
    --outfile ~/models/gemma-4-e2b-f16.gguf \
    --outtype f16

# 3. Quantize to a mobile-friendly variant
./llama-quantize ~/models/gemma-4-e2b-f16.gguf \
    ~/models/gemma-4-e2b-q4_k_m.gguf \
    Q4_K_M

# 4. Compute sha256
sha256sum ~/models/gemma-4-e2b-q4_k_m.gguf
```

### Quantization tier recommendations for mobile

| Quant     | Size factor vs fp16 | Quality              | When to use                                |
|-----------|---------------------|----------------------|--------------------------------------------|
| Q8_0      | ~0.53×              | Near-lossless        | Flagship phones with RAM to spare          |
| Q6_K      | ~0.41×              | Very high            | High-end phones                            |
| **Q5_K_M**| ~0.34×              | High                 | Sweet spot on flagships                    |
| **Q4_K_M**| ~0.28×              | Good                 | **Default for mobile**                     |
| Q4_0      | ~0.27×              | Good                 | Slightly worse than K-quants               |
| Q3_K_M    | ~0.21×              | Degraded             | Last resort for fitting on low-RAM devices |
| Q2_K      | ~0.18×              | Notably degraded     | Avoid unless you really need the size      |
| IQ4_NL    | ~0.27×              | Often beats Q4_K_M   | Worth testing per-model                    |
| IQ3_M     | ~0.22×              | Good for size        | Tight-RAM target devices                   |

Default to Q4_K_M unless there's a specific reason. Ship multiple quants
of the same model so the user (or auto-detection) can pick by RAM.

## Catalog format

`app/src/main/assets/catalog.json` is a JSON object with a top-level array.
Each entry is one quantization variant.

```json
{
  "version": 1,
  "models": [
    {
      "id": "gemma-4-e2b-q4_k_m",
      "family": "gemma-4",
      "display_name": "Gemma 4 E2B (Q4_K_M)",
      "description": "Google's mobile-optimized 2.3B effective-param model.",
      "url": "https://huggingface.co/edge-llm/gemma-4-e2b-gguf/resolve/main/gemma-4-e2b-q4_k_m.gguf",
      "sha256": "abc123...",
      "size_bytes": 1400000000,
      "quantization": "Q4_K_M",
      "context_length": 128000,
      "min_ram_mb": 3000,
      "recommended_ram_mb": 6000,
      "chat_template": "gemma-4",
      "stop_tokens": ["<end_of_turn>"],
      "modalities": ["text"],
      "engine": "llamacpp",
      "license": "Gemma Terms of Use",
      "license_url": "https://ai.google.dev/gemma/terms"
    }
  ]
}
```

### Field semantics

| Field               | Source / how to determine                                         |
|---------------------|-------------------------------------------------------------------|
| `id`                | Stable kebab-case: `<family>-<size>-<quant>`                      |
| `family`            | Maps to a `ChatTemplate` registered in `core/domain`              |
| `url`               | Where the GGUF lives. Must be HTTPS, must support Range requests. |
| `sha256`            | `sha256sum` of the GGUF — verified after download                 |
| `size_bytes`        | File size on disk; used for download UI and storage planning      |
| `context_length`    | Max usable context — from the model card or measured              |
| `min_ram_mb`        | Refuse to load below this. Compute: model size + KV cache @ 4K ctx + 500 MB headroom |
| `recommended_ram_mb`| Below this, expect slow/laggy. UI warns.                          |
| `chat_template`     | Must match a key in `ChatTemplateRegistry`                        |
| `stop_tokens`       | Inference stops on first match. From the model's generation_config or tokenizer config. |
| `modalities`        | `["text"]` for now. Add `"image"`, `"audio"` when we support them.|
| `engine`            | `"llamacpp"` for v1. Future: `"mlc"`, `"aicore"`                  |
| `license`           | Human-readable license name; shown in UI                          |
| `license_url`       | Link displayed before download                                    |

### Schema validation

`catalog.json` is validated at app boot. Failure = the offending entry is
skipped (logged), not a crash. Schema is enforced by a kotlinx.serialization
`@Serializable` class with required fields; unknown fields are ignored to
allow forward compatibility.

## Remote catalog overlay (optional)

In Settings, the user may configure a remote catalog URL. On launch (and
on demand), the app fetches it, validates, and merges with the bundled
catalog:

- Entries with the same `id` → remote wins
- Entries only in remote → added
- Entries only bundled → kept

Use case: ship new model recommendations without releasing an app update.

## Download flow

1. User selects a model in the picker.
2. `ModelRepository.materialize(descriptor)` is called.
3. If file exists at `filesDir/models/<id>.gguf` and sha256 matches → return immediately.
4. Otherwise: enqueue `DownloadWorker` (WorkManager).
5. Default constraints: unmetered network + charging. User can override per download.
6. Progress reported via `WorkManager.getWorkInfoByIdFlow()` → UI shows percentage.
7. On completion, sha256 is verified. Mismatch → file deleted, work failed,
   user notified.
8. On success, returns `ModelHandle(path, descriptor)`.

## Storage management

- Models live in app-internal `filesDir/models/`.
- A "Manage storage" screen lists installed models with size, last-used date,
  and a delete action.
- On `onLowMemory()`, the app does NOT auto-delete models — too destructive.

## Adding a new model family

When a model from a new family (e.g., a new "DeepSeek-tiny") needs supporting:

1. Add `DeepSeekTemplate : ChatTemplate` in `core/domain/templates/`.
2. Register it in `ChatTemplateRegistry` under the family key.
3. Write its unit tests (system prompt, multi-turn, edge cases).
4. Add the catalog entry with `chat_template: "deepseek-tiny"`.
5. If the model needs special sampling defaults, document in the entry's
   `description` and/or augment `GenerationConfig` defaults per family.

No engine changes — llama.cpp loads any GGUF regardless of family.

## Testing the pipeline

- A tiny test model (a 270M-param or smaller GGUF) lives in
  `core/engine/llamacpp/src/androidTest/assets/`.
- It is too small to be useful, by design — it's there so JNI / load /
  unload / cancel tests can run on real devices quickly.
- The catalog parser has unit tests against valid and intentionally
  malformed JSON in `src/test/resources/`.
