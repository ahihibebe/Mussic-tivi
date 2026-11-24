package com.example.mobile.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.mobile.model.MobileAudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class PhoneMusicScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "mid"
    )

    private val coverFileNames = listOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "album.jpg", "album.png", "front.jpg", "front.png"
    )

    suspend fun scanAll(extraTreeUris: Set<Uri> = emptySet()): List<MobileAudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MobileAudioTrack>()
        val seenKeys = mutableSetOf<String>()

        // 1. Scan Android MediaStore
        try {
            scanMediaStore().forEach { track ->
                val dedupKey = track.filePath.ifBlank { track.contentUri ?: track.id }
                if (seenKeys.add(dedupKey)) {
                    tracks.add(track)
                }
            }
        } catch (e: Exception) {
            Log.e("PhoneMusicScanner", "MediaStore query error", e)
        }

        // 2. Scan standard public directories (Music, Downloads, Root)
        val candidateDirs = mutableListOf<File>()
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { candidateDirs.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { candidateDirs.add(it) }
            Environment.getExternalStorageDirectory()?.let { candidateDirs.add(it) }
        } catch (e: Exception) {
            Log.e("PhoneMusicScanner", "Storage roots error", e)
        }

        for (dir in candidateDirs) {
            if (dir.exists() && dir.canRead()) {
                scanDirectoryRecursively(dir, tracks, seenKeys, maxDepth = 4)
            }
        }

        // 3. Scan any SAF folders picked manually by the user
        extraTreeUris.forEach { treeUri ->
            try {
                scanDocumentTree(treeUri, tracks, seenKeys)
            } catch (e: Exception) {
                Log.e("PhoneMusicScanner", "SAF tree scan error", e)
            }
        }

        tracks.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    private fun scanMediaStore(): List<MobileAudioTrack> {
        val tracks = mutableListOf<MobileAudioTrack>()
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
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID
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
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Bài hát #$id"
                val artist = cursor.getString(artistCol) ?: "Nghệ sĩ chưa rõ"
                val album = cursor.getString(albumCol) ?: "Điện thoại"
                val duration = cursor.getLong(durationCol)
                val path = cursor.getString(dataCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val mime = cursor.getString(mimeCol) ?: ""
                val albumId = cursor.getLong(albumIdCol)

                val file = if (path.isNotEmpty()) File(path) else null
                val ext = file?.extension?.lowercase(Locale.ROOT).orEmpty()
                if (supportedExtensions.contains(ext) || mime.startsWith("audio/")) {
                    val format = determineFormat(ext, mime)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val parent = file?.parent ?: ""
                    val albumArt = resolveMediaStoreOrFolderArt(albumId, file, contentUri)

                    tracks.add(
                        MobileAudioTrack(
                            id = "ms_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            filePath = path,
                            contentUri = contentUri,
                            format = format,
                            bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps",
                            fileSizeBytes = size,
                            parentDirectory = parent,
                            albumArtUri = albumArt
                        )
                    )
                }
            }
        }
        return tracks
    }

    private fun scanDirectoryRecursively(
        dir: File,
        outTracks: MutableList<MobileAudioTrack>,
        seenKeys: MutableSet<String>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth || !dir.canRead()) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectoryRecursively(file, outTracks, seenKeys, maxDepth, currentDepth + 1)
            } else if (file.isFile) {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (supportedExtensions.contains(ext) && seenKeys.add(file.absolutePath)) {
                    outTracks.add(parseFileTrackMetadata(file, ext))
                }
            }
        }
    }

    private fun scanDocumentTree(
        treeUri: Uri,
        outTracks: MutableList<MobileAudioTrack>,
        seenKeys: MutableSet<String>
    ) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        scanDocumentFolder(treeUri, rootDocId, outTracks, seenKeys, depth = 0, maxDepth = 4)
    }

    private fun scanDocumentFolder(
        treeUri: Uri,
        parentDocId: String,
        outTracks: MutableList<MobileAudioTrack>,
        seenKeys: MutableSet<String>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val docIdCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(docIdCol)
                val name = cursor.getString(nameCol) ?: continue
                val mime = cursor.getString(mimeCol) ?: ""
                val size = cursor.getLong(sizeCol)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) {
                        scanDocumentFolder(treeUri, docId, outTracks, seenKeys, depth + 1, maxDepth)
                    }
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (supportedExtensions.contains(ext) || mime.startsWith("audio/")) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val uriStr = docUri.toString()
                        if (seenKeys.add(uriStr)) {
                            outTracks.add(parseSafTrackMetadata(docUri, name, ext, mime, size))
                        }
                    }
                }
            }
        }
    }

    private fun parseSafTrackMetadata(
        docUri: Uri,
        displayName: String,
        ext: String,
        mime: String,
        size: Long
    ): MobileAudioTrack {
        val format = determineFormat(ext, mime)
        var title = displayName.substringBeforeLast('.')
        var artist = "Nghệ sĩ chưa rõ"
        var album = "Thư mục đã chọn"
        var durationMs = 0L
        var bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps"
        var albumArtPath: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, docUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { bps ->
                if (bps > 0) bitrate = "${bps / 1000} kbps"
            }
            albumArtPath = cacheEmbeddedPicture(docUri.toString(), retriever.embeddedPicture)
        } catch (ignored: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        return MobileAudioTrack(
            id = "saf_${docUri.toString().hashCode()}",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filePath = "",
            contentUri = docUri.toString(),
            format = format,
            bitrate = bitrate,
            fileSizeBytes = size,
            parentDirectory = "Thư mục đã chọn",
            albumArtUri = albumArtPath
        )
    }

    private fun parseFileTrackMetadata(file: File, ext: String): MobileAudioTrack {
        val format = ext.uppercase(Locale.ROOT)
        var title = file.nameWithoutExtension
        var artist = "Nghệ sĩ chưa rõ"
        var album = file.parentFile?.name ?: "Điện thoại"
        var durationMs = 0L
        var bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps"
        var albumArtUri: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { bps ->
                if (bps > 0) bitrate = "${bps / 1000} kbps"
            }
            albumArtUri = cacheEmbeddedPicture(file.absolutePath, retriever.embeddedPicture)
        } catch (ignored: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        if (albumArtUri == null) {
            albumArtUri = findFolderCoverArt(file.parentFile)
        }

        return MobileAudioTrack(
            id = "file_${file.absolutePath.hashCode()}",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filePath = file.absolutePath,
            format = format,
            bitrate = bitrate,
            fileSizeBytes = file.length(),
            parentDirectory = file.parent ?: "",
            albumArtUri = albumArtUri
        )
    }

    private fun resolveMediaStoreOrFolderArt(albumId: Long, file: File?, contentUri: String): String? {
        if (albumId > 0) {
            val artUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
            try {
                context.contentResolver.openInputStream(artUri)?.use {
                    return artUri.toString()
                }
            } catch (ignored: Exception) {
            }
        }

        val cacheKey = file?.absolutePath ?: contentUri
        val cacheFile = File(getAlbumArtCacheDir(), "${cacheKey.hashCode()}.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return cacheFile.absolutePath
        }

        val retriever = MediaMetadataRetriever()
        try {
            if (file != null && file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else {
                retriever.setDataSource(context, Uri.parse(contentUri))
            }
            val embedded = cacheEmbeddedPicture(cacheKey, retriever.embeddedPicture)
            if (embedded != null) return embedded
        } catch (ignored: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        return findFolderCoverArt(file?.parentFile)
    }

    private fun cacheEmbeddedPicture(cacheKey: String, pictureBytes: ByteArray?): String? {
        if (pictureBytes == null || pictureBytes.isEmpty()) return null
        return try {
            val artDir = getAlbumArtCacheDir()
            val outFile = File(artDir, "${cacheKey.hashCode()}.jpg")
            if (!outFile.exists() || outFile.length() == 0L) {
                FileOutputStream(outFile).use { it.write(pictureBytes) }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun findFolderCoverArt(folder: File?): String? {
        if (folder == null || !folder.exists() || !folder.isDirectory) return null
        val files = folder.listFiles() ?: return null
        for (candidateName in coverFileNames) {
            val match = files.firstOrNull { it.isFile && it.name.equals(candidateName, ignoreCase = true) }
            if (match != null) return match.absolutePath
        }
        return null
    }

    private fun getAlbumArtCacheDir(): File {
        return File(context.cacheDir, "album_art").apply { mkdirs() }
    }

    private fun determineFormat(ext: String, mime: String): String {
        return when {
            ext.equals("flac", ignoreCase = true) || mime.contains("flac") -> "FLAC"
            ext.equals("wav", ignoreCase = true) || mime.contains("wav") -> "WAV"
            ext.equals("ogg", ignoreCase = true) || mime.contains("ogg") -> "OGG"
            ext.equals("opus", ignoreCase = true) || mime.contains("opus") -> "OPUS"
            ext.equals("aac", ignoreCase = true) || mime.contains("aac") -> "AAC"
            ext.equals("m4a", ignoreCase = true) || mime.contains("mp4") -> "M4A"
            ext.equals("wma", ignoreCase = true) -> "WMA"
            ext.equals("mid", ignoreCase = true) -> "MIDI"
            else -> "MP3"
        }
    }
}
