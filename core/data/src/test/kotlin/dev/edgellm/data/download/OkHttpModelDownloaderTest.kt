package dev.edgellm.data.download

import dev.edgellm.domain.model.ModelDescriptor
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

class OkHttpModelDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var downloader: OkHttpModelDownloader

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
        downloader = OkHttpModelDownloader(client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun descriptorFor(url: String, sha256: String, sizeBytes: Long = 100L) = ModelDescriptor(
        id = "test-model",
        family = "falcon-e",
        displayName = "Test",
        url = url,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        quantization = "i2_s",
        contextLength = 2048,
        minRamMb = 800,
        recommendedRamMb = 1200,
        chatTemplate = "falcon-e",
        stopTokens = listOf("<|im_end|>"),
        engine = "llamacpp",
    )

    @Test
    fun `emits progress and complete on successful download`() = runTest {
        val content = "fake model data".toByteArray()
        val hash = sha256(content)
        val descriptor = descriptorFor(server.url("/model.gguf").toString(), hash, content.size.toLong())

        server.enqueue(MockResponse()
            .setBody(Buffer().write(content))
            .setHeader("Content-Length", content.size))

        val events = downloader.download(descriptor, tempDir.absolutePath).toList()

        assertTrue(events.first() is DownloadProgress.Queued)
        assertTrue(events.any { it is DownloadProgress.Downloading })
        assertTrue(events.any { it is DownloadProgress.Verifying })
        assertTrue(events.last() is DownloadProgress.Complete)

        val complete = events.last() as DownloadProgress.Complete
        assertTrue(File(complete.localPath).exists())
    }

    @Test
    fun `fails on sha256 mismatch`() = runTest {
        val content = "fake model data".toByteArray()
        val descriptor = descriptorFor(server.url("/model.gguf").toString(), "wrong-hash")

        server.enqueue(MockResponse()
            .setBody(Buffer().write(content))
            .setHeader("Content-Length", content.size))

        val events = downloader.download(descriptor, tempDir.absolutePath).toList()

        assertTrue(events.last() is DownloadProgress.Failed)
        val failed = events.last() as DownloadProgress.Failed
        assertTrue(failed.cause.message!!.contains("SHA256 mismatch"))
    }

    @Test
    fun `fails on http error`() = runTest {
        val descriptor = descriptorFor(server.url("/model.gguf").toString(), "abc")

        server.enqueue(MockResponse().setResponseCode(404))

        val events = downloader.download(descriptor, tempDir.absolutePath).toList()

        assertTrue(events.last() is DownloadProgress.Failed)
    }

    @Test
    fun `succeeds when sha256 is blank`() = runTest {
        val content = "fake model data".toByteArray()
        val descriptor = descriptorFor(server.url("/model.gguf").toString(), "", content.size.toLong())

        server.enqueue(MockResponse()
            .setBody(Buffer().write(content))
            .setHeader("Content-Length", content.size))

        val events = downloader.download(descriptor, tempDir.absolutePath).toList()

        assertTrue(events.first() is DownloadProgress.Queued)
        assertTrue(events.any { it is DownloadProgress.Verifying })
        assertTrue(events.last() is DownloadProgress.Complete)
    }

    @Test
    fun `isDownloading returns false when not downloading`() {
        assertTrue(!downloader.isDownloading("test"))
    }
}
