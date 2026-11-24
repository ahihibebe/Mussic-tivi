package com.example.mobile.model

data class MobileAudioTrack(
    val id: String,
    val title: String,
    val artist: String = "Nghệ sĩ chưa rõ",
    val album: String = "Điện thoại",
    val durationMs: Long,
    val filePath: String,
    val contentUri: String? = null,
    val format: String,
    val bitrate: String = "320 kbps",
    val fileSizeBytes: Long = 0L,
    val parentDirectory: String = "",
    val albumArtUri: String? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
