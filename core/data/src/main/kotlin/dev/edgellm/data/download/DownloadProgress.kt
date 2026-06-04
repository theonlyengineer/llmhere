package dev.edgellm.data.download

sealed interface DownloadProgress {
    data object Queued : DownloadProgress
    data class Downloading(
        val percent: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadProgress
    data object Verifying : DownloadProgress
    data class Complete(val localPath: String) : DownloadProgress
    data class Failed(val cause: Throwable) : DownloadProgress
}
