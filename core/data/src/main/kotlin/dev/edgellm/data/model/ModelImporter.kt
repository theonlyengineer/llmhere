package dev.edgellm.data.model

import dev.edgellm.domain.model.InstalledModel
import dev.edgellm.domain.model.ModelDescriptor
import java.io.File

class ModelImporter(
    private val repository: InstalledModelRepository,
    private val modelsDir: String,
) {

    sealed interface ImportResult {
        data class Success(val model: InstalledModel) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    suspend fun import(sourceFile: File, descriptor: ModelDescriptor): ImportResult {
        if (!sourceFile.exists()) {
            return ImportResult.Error("Source file does not exist: ${sourceFile.absolutePath}")
        }

        if (!isValidGguf(sourceFile)) {
            return ImportResult.Error("Not a valid GGUF file")
        }

        val destDir = File(modelsDir)
        destDir.mkdirs()
        val destFile = File(destDir, "${descriptor.id}.gguf")

        sourceFile.copyTo(destFile, overwrite = true)

        val model = repository.markInstalled(descriptor, destFile.absolutePath)
        return ImportResult.Success(model)
    }

    companion object {
        // GGUF magic bytes: "GGUF" = 0x47 0x47 0x55 0x46
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46)

        fun isValidGguf(file: File): Boolean {
            if (!file.exists() || file.length() < 4) return false
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            return header.contentEquals(GGUF_MAGIC)
        }
    }
}
