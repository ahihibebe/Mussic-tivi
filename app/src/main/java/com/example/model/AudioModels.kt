package com.example.model

data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String = "Nghệ sĩ chưa rõ",
    val album: String = "Album",
    val durationMs: Long,
    val filePath: String,
    val contentUri: String? = null,
    val format: String, // MP3, FLAC, WAV, AAC, OGG, OPUS, M4A
    val bitrate: String = "320 kbps",
    val sampleRate: String = "44.1 kHz",
    val fileSizeBytes: Long = 0L,
    val parentDirectory: String = "",
    val isFavorite: Boolean = false,
    val isDemo: Boolean = false,
    val albumArtUri: String? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val formattedSize: String
        get() {
            if (fileSizeBytes <= 0) return "Lossless"
            val mb = fileSizeBytes.toDouble() / (1024 * 1024)
            return "%.1f MB".format(mb)
        }
}

data class AudioFolder(
    val path: String,
    val name: String,
    val trackCount: Int,
    val subfolderCount: Int,
    val parentPath: String? = null,
    val isRoot: Boolean = false
)

enum class RepeatMode(val label: String) {
    OFF("Tắt lặp"),
    ALL("Lặp tất cả"),
    ONE("Lặp bài này")
}

enum class EqualizerPreset(
    val title: String,
    val bass: Float, // -1.0 to +1.0
    val mid: Float,
    val treble: Float
) {
    NORMAL("Mặc định", 0f, 0f, 0f),
    BASS_BOOST("Tăng Bass trầm", 0.8f, 0.2f, -0.1f),
    VOCAL("Giọng hát / Acoustic", -0.2f, 0.7f, 0.4f),
    ROCK("Nhạc Rock", 0.6f, -0.2f, 0.7f),
    POP("Nhạc Pop", 0.4f, 0.4f, 0.3f),
    JAZZ("Nhạc Jazz", 0.3f, 0.2f, 0.6f),
    ELECTRONIC("EDM / Điện tử", 0.9f, -0.1f, 0.8f),
    CINEMA("Xem phim / TV", 0.5f, 0.5f, 0.5f)
}

enum class TvNavTab(val title: String, val iconName: String) {
    FOLDERS("Thư mục máy", "folder"),
    ALL_SONGS("Tất cả bài hát", "music"),
    NOW_PLAYING("Đang phát", "play_circle"),
    PLAYLISTS("Danh sách & Yêu thích", "favorite"),
    SETTINGS("Cài đặt & Âm thanh", "settings")
}

enum class SortOption(val label: String) {
    TITLE("Tên bài hát A-Z"),
    ARTIST("Tên nghệ sĩ"),
    DURATION("Thời lượng"),
    FORMAT("Định dạng (FLAC, MP3...)")
}
