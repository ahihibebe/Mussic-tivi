package com.example.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class StorageScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "mid"
    )

    data class ScanResult(
        val tracks: List<AudioTrack>,
        val folders: List<AudioFolder>
    )

    suspend fun scanAll(includeDemo: Boolean = true): ScanResult = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<AudioTrack>()
        val seenPaths = mutableSetOf<String>()

        // 1. Scan MediaStore if permission is available
        try {
            val mediaStoreTracks = scanMediaStore()
            for (track in mediaStoreTracks) {
                if (seenPaths.add(track.filePath)) {
                    tracksList.add(track)
                }
            }
        } catch (e: Exception) {
            Log.e("StorageScanner", "Error querying MediaStore", e)
        }

        // 2. Scan standard device storage folders & external mounts (USB on TV)
        val candidateDirs = mutableListOf<File>()
        
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { candidateDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { candidateDirs.add(it) }
            Environment.getExternalStorageDirectory()?.let { candidateDirs.add(it) }

            // TV USB Drives and SD cards are typically mounted under /storage
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                storageRoot.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.canRead() && !file.name.equals("self", ignoreCase = true) && !file.name.equals("emulated", ignoreCase = true)) {
                        candidateDirs.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("StorageScanner", "Error checking storage roots", e)
        }

        for (dir in candidateDirs) {
            if (dir.exists() && dir.canRead()) {
                scanDirectoryRecursively(dir, tracksList, seenPaths, maxDepth = 4)
            }
        }

        // 3. Include Demo Tracks so the TV app is immediately functional
        if (includeDemo) {
            try {
                val demoTracks = com.example.audio.DemoAudioGenerator.getOrCreateDemoTracks(context)
                for (demo in demoTracks) {
                    if (seenPaths.add(demo.filePath)) {
                        tracksList.add(demo)
                    }
                }
            } catch (e: Exception) {
                Log.e("StorageScanner", "Error generating demo tracks", e)
            }
        }

        // 4. Build Folders representation
        val folders = buildFolderHierarchy(tracksList)

        ScanResult(tracksList, folders)
    }

    suspend fun scanCustomDirectory(directoryPath: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        val target = File(directoryPath)
        val tracks = mutableListOf<AudioTrack>()
        val seen = mutableSetOf<String>()
        if (target.exists() && target.isDirectory) {
            scanDirectoryRecursively(target, tracks, seen, maxDepth = 5)
        }
        tracks
    }

    private fun scanMediaStore(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Bài hát #$id"
                val artist = cursor.getString(artistCol) ?: "Nghệ sĩ chưa rõ"
                val album = cursor.getString(albumCol) ?: "Album"
                val duration = cursor.getLong(durationCol)
                val path = cursor.getString(dataCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: ""

                if (path.isNotEmpty()) {
                    val file = File(path)
                    val ext = file.extension.lowercase(Locale.ROOT)
                    if (supportedExtensions.contains(ext) || ext.isNotEmpty()) {
                        val format = determineFormat(ext, mime)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                        val parent = file.parent ?: ""

                        tracks.add(
                            AudioTrack(
                                id = "ms_$id",
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                filePath = path,
                                contentUri = contentUri,
                                format = format,
                                bitrate = "320 kbps",
                                fileSizeBytes = size,
                                parentDirectory = parent,
                                isDemo = false
                            )
                        )
                    }
                }
            }
        }
        return tracks
    }

    private fun scanDirectoryRecursively(
        dir: File,
        outTracks: MutableList<AudioTrack>,
        seenPaths: MutableSet<String>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth || !dir.canRead()) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectoryRecursively(file, outTracks, seenPaths, maxDepth, currentDepth + 1)
            } else if (file.isFile) {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (supportedExtensions.contains(ext) && seenPaths.add(file.absolutePath)) {
                    val track = parseTrackMetadata(file, ext)
                    outTracks.add(track)
                }
            }
        }
    }

    private fun parseTrackMetadata(file: File, ext: String): AudioTrack {
        val format = ext.uppercase(Locale.ROOT)
        var title = file.nameWithoutExtension
        var artist = "Nghệ sĩ chưa rõ"
        var album = file.parentFile?.name ?: "Bộ nhớ máy"
        var durationMs = 0L
        var bitrate = "320 kbps"
        var sampleRate = "44.1 kHz"

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val metaBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum
            if (!metaDuration.isNullOrBlank()) durationMs = metaDuration.toLongOrNull() ?: 0L
            if (!metaBitrate.isNullOrBlank()) {
                val bps = metaBitrate.toLongOrNull() ?: 0L
                if (bps > 0) bitrate = "${bps / 1000} kbps"
            }
        } catch (e: Exception) {
            // Fallback gracefully to filename
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        if (format == "FLAC" || format == "WAV") {
            bitrate = "Lossless"
            sampleRate = "96.0 kHz"
        }

        return AudioTrack(
            id = "file_${file.hashCode()}",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filePath = file.absolutePath,
            format = format,
            bitrate = bitrate,
            sampleRate = sampleRate,
            fileSizeBytes = file.length(),
            parentDirectory = file.parent ?: "",
            isDemo = false
        )
    }

    private fun buildFolderHierarchy(tracks: List<AudioTrack>): List<AudioFolder> {
        val folderMap = mutableMapOf<String, MutableList<AudioTrack>>()
        for (track in tracks) {
            val dir = track.parentDirectory.ifEmpty { "Bộ nhớ trong" }
            folderMap.getOrPut(dir) { mutableListOf() }.add(track)
        }

        return folderMap.map { (path, trackList) ->
            val file = File(path)
            val folderName = if (path.contains("demo_music")) {
                "Nhạc mẫu chất lượng cao (Demo)"
            } else if (file.name.isNotEmpty()) {
                file.name
            } else {
                path
            }

            val subfolders = file.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.size ?: 0

            AudioFolder(
                path = path,
                name = folderName,
                trackCount = trackList.size,
                subfolderCount = subfolders,
                parentPath = file.parent,
                isRoot = path == "/" || path == "/storage"
            )
        }.sortedByDescending { it.trackCount }
    }

    private fun determineFormat(ext: String, mime: String): String {
        return when {
            ext.equals("flac", ignoreCase = true) || mime.contains("flac") -> "FLAC"
            ext.equals("wav", ignoreCase = true) || mime.contains("wav") -> "WAV"
            ext.equals("ogg", ignoreCase = true) || mime.contains("ogg") -> "OGG"
            ext.equals("opus", ignoreCase = true) || mime.contains("opus") -> "OPUS"
            ext.equals("aac", ignoreCase = true) || mime.contains("aac") -> "AAC"
            ext.equals("m4a", ignoreCase = true) -> "M4A"
            ext.equals("wma", ignoreCase = true) -> "WMA"
            ext.equals("mid", ignoreCase = true) -> "MIDI"
            else -> "MP3"
        }
    }
}
