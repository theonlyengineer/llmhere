package dev.edgellm.data.model

import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ModelCatalog {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(jsonString: String): List<ModelDescriptor> {
        return try {
            val entries = json.decodeFromString<List<CatalogEntry>>(jsonString)
            entries.mapNotNull { it.toModelDescriptor() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class CatalogEntry(
        val id: String? = null,
        val family: String? = null,
        @SerialName("display_name") val displayName: String? = null,
        val url: String? = null,
        val sha256: String? = null,
        @SerialName("size_bytes") val sizeBytes: Long? = null,
        val quantization: String? = null,
        @SerialName("context_length") val contextLength: Int? = null,
        @SerialName("min_ram_mb") val minRamMb: Int? = null,
        @SerialName("recommended_ram_mb") val recommendedRamMb: Int? = null,
        @SerialName("chat_template") val chatTemplate: String? = null,
        @SerialName("stop_tokens") val stopTokens: List<String>? = null,
        val engine: String? = null,
    ) {
        fun toModelDescriptor(): ModelDescriptor? {
            return try {
                ModelDescriptor(
                    id = id ?: return null,
                    family = family ?: return null,
                    displayName = displayName ?: return null,
                    url = url ?: return null,
                    sha256 = sha256 ?: return null,
                    sizeBytes = sizeBytes ?: return null,
                    quantization = quantization ?: return null,
                    contextLength = contextLength ?: return null,
                    minRamMb = minRamMb ?: return null,
                    recommendedRamMb = recommendedRamMb ?: return null,
                    chatTemplate = chatTemplate ?: return null,
                    stopTokens = stopTokens ?: return null,
                    engine = engine ?: return null,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
