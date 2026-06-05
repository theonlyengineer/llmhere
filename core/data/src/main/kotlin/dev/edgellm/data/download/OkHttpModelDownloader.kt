package dev.edgellm.data.download

import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class OkHttpModelDownloader(
    private val client: OkHttpClient,
) : ModelDownloader {

    private val activeDownloads = ConcurrentHashMap<String, Boolean>()

    override fun download(descriptor: ModelDescriptor, destDir: String): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Queued)
        activeDownloads[descriptor.id] = true

        try {
            val destFile = File(destDir, "${descriptor.id}.gguf")
            destFile.parentFile?.mkdirs()

            val existingBytes = if (destFile.exists()) destFile.length() else 0L

            val requestBuilder = Request.Builder().url(descriptor.url)
            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                emit(DownloadProgress.Failed(RuntimeException("HTTP ${response.code}")))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadProgress.Failed(RuntimeException("Empty response body")))
                return@flow
            }

            val totalBytes = if (response.code == 206) {
                existingBytes + body.contentLength()
            } else {
                body.contentLength()
            }
            val append = response.code == 206

            body.byteStream().use { input ->
                val output = if (append) destFile.outputStream().apply {
                    channel.position(existingBytes)
                } else destFile.outputStream()

                output.use { out ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = if (append) existingBytes else 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (activeDownloads[descriptor.id] != true) {
                            emit(DownloadProgress.Failed(RuntimeException("Cancelled")))
                            return@flow
                        }
                        out.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val percent = if (totalBytes > 0) {
                            (bytesDownloaded.toFloat() / totalBytes * 100f)
                        } else 0f
                        emit(DownloadProgress.Downloading(percent, bytesDownloaded, totalBytes))
                    }
                }
            }

            emit(DownloadProgress.Verifying)

            if (descriptor.sha256.isNotBlank()) {
                val actualSha256 = sha256(destFile)
                if (actualSha256 != descriptor.sha256) {
                    destFile.delete()
                    emit(DownloadProgress.Failed(
                        RuntimeException("SHA256 mismatch: expected=${descriptor.sha256}, actual=$actualSha256")
                    ))
                    return@flow
                }
            }

            emit(DownloadProgress.Complete(destFile.absolutePath))
        } catch (e: Exception) {
            emit(DownloadProgress.Failed(e))
        } finally {
            activeDownloads.remove(descriptor.id)
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel(modelId: String) {
        activeDownloads[modelId] = false
    }

    override fun isDownloading(modelId: String): Boolean =
        activeDownloads[modelId] == true

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
