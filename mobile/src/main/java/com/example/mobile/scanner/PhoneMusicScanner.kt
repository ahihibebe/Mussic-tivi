package com.example.mobile.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.mobile.model.MobileAudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class MobileScanProgress(
    val tracks: List<MobileAudioTrack>,
    val stageLabel: String,
    val foundCount: Int,
    val latestTrackTitle: String?
)

class PhoneMusicScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "mid"
    )

    private val coverFileNames = listOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "album.jpg", "album.png", "front.jpg", "front.png"
    )

    suspend fun scanAll(
        extraTreeUris: Set<Uri> = emptySet(),
        extraFileUris: Set<Uri> = emptySet(),
        excludedKeys: Set<String> = emptySet(),
        onProgress: ((MobileScanProgress) -> Unit)? = null
    ): List<MobileAudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MobileAudioTrack>()
        val seenKeys = mutableSetOf<String>()
        val seenFingerprints = mutableSetOf<String>()
        val folderCoverCache = mutableMapOf<String, String?>()
        var lastEmitTime = 0L

        fun isExcluded(track: MobileAudioTrack): Boolean {
            if (excludedKeys.isEmpty()) return false
            if (excludedKeys.contains(track.id)) return true
            if (track.filePath.isNotBlank() && excludedKeys.contains(track.filePath)) return true
            if (!track.contentUri.isNullOrBlank() && excludedKeys.contains(track.contentUri)) return true
            return false
        }

        fun emitProgress(stage: String, latestTitle: String?, force: Boolean = false) {
            if (onProgress == null) return
            val now = System.currentTimeMillis()
            if (force || now - lastEmitTime >= 450L) {
                lastEmitTime = now
                onProgress(
                    MobileScanProgress(
                        tracks = tracks.toList(),
                        stageLabel = stage,
                        foundCount = tracks.size,
                        latestTrackTitle = latestTitle
                    )
                )
            }
        }

        fun tryAddTrack(track: MobileAudioTrack, stage: String): Boolean {
            if (isExcluded(track)) return false
            val primaryKey = track.filePath.ifBlank { track.contentUri ?: track.id }
            val fileName = track.filePath.takeIf { it.isNotBlank() }?.let {
                it.substringAfterLast('/')
            } ?: track.title
            val fingerprint = "${fileName.lowercase(Locale.ROOT)}|${track.fileSizeBytes}"
            val isDuplicateFingerprint = track.fileSizeBytes > 0L && !seenFingerprints.add(fingerprint)
            if (seenKeys.add(primaryKey) && !isDuplicateFingerprint) {
                tracks.add(track)
                emitProgress(stage, track.title, force = tracks.size <= 3)
                return true
            }
            return false
        }

        currentCoroutineContext().ensureActive()
        emitProgress("Đang quét nhanh MediaStore...", null, force = true)

        // 1. Fast SQLite query on MediaStore.Audio (ALL audio, no IS_MUSIC != 0 filter)
        try {
            scanMediaStoreAudio(folderCoverCache) { track ->
                tryAddTrack(track, "Đang quét kho nhạc MediaStore...")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("PhoneMusicScanner", "MediaStore Audio query error", e)
        }

        currentCoroutineContext().ensureActive()

        // 2. Fast SQLite query on MediaStore.Downloads (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            emitProgress("Đang quét mục Download (MediaStore)...", tracks.lastOrNull()?.title, force = true)
            try {
                scanMediaStoreDownloads(folderCoverCache) { track ->
                    tryAddTrack(track, "Đang quét mục Download...")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("PhoneMusicScanner", "MediaStore Downloads query error", e)
            }
        }

        // 3. Targeted lightweight File fallback ONLY for Download & Music (depth <= 2)
        // MediaStore already covers Music/Recordings/Zalo/Telegram/Documents SQLite entries instantaneously.
        val candidateDirs = linkedSetOf<Pair<String, File>>()
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                candidateDirs.add("Đang quét thư mục Download..." to it)
            }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let {
                candidateDirs.add("Đang quét thư mục Music..." to it)
            }
            Environment.getExternalStorageDirectory()?.let { root ->
                candidateDirs.add("Đang quét thư mục Download..." to File(root, "Download"))
                candidateDirs.add("Đang quét thư mục Music..." to File(root, "Music"))
            }
        } catch (e: Exception) {
            Log.e("PhoneMusicScanner", "Storage roots error", e)
        }

        val visitedDirs = mutableSetOf<String>()
        for ((stageLabel, dir) in candidateDirs) {
            currentCoroutineContext().ensureActive()
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (visitedDirs.add(canonical) && dir.exists() && dir.canRead()) {
                emitProgress(stageLabel, tracks.lastOrNull()?.title, force = true)
                scanDirectoryFast(
                    dir = dir,
                    seenKeys = seenKeys,
                    folderCoverCache = folderCoverCache,
                    maxDepth = 2
                ) { found ->
                    tryAddTrack(found, stageLabel)
                }
            }
        }

        // 4. Scan SAF folders picked manually by the user
        for (treeUri in extraTreeUris) {
            currentCoroutineContext().ensureActive()
            emitProgress("Đang quét thư mục đã chọn...", tracks.lastOrNull()?.title, force = true)
            try {
                scanDocumentTreeFast(treeUri, seenKeys, folderCoverCache) { found ->
                    tryAddTrack(found, "Đang quét thư mục đã chọn...")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("PhoneMusicScanner", "SAF tree scan error for $treeUri", e)
            }
        }

        // 5. Scan individual SAF files picked manually
        if (extraFileUris.isNotEmpty()) {
            emitProgress("Đang nạp file nhạc đã chọn...", tracks.lastOrNull()?.title, force = true)
            for (fileUri in extraFileUris) {
                currentCoroutineContext().ensureActive()
                try {
                    parsePickedFileUriFast(fileUri)?.let { found ->
                        tryAddTrack(found, "Đang nạp file nhạc đã chọn...")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("PhoneMusicScanner", "SAF file scan error for $fileUri", e)
                }
            }
        }

        val finalList = tracks.sortedBy { it.title.lowercase(Locale.ROOT) }
        onProgress?.invoke(
            MobileScanProgress(
                tracks = finalList,
                stageLabel = "Quét hoàn tất",
                foundCount = finalList.size,
                latestTrackTitle = null
            )
        )
        finalList
    }

    suspend fun scanSingleTree(
        treeUri: Uri,
        excludedKeys: Set<String> = emptySet()
    ): List<MobileAudioTrack> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MobileAudioTrack>()
        val seenKeys = mutableSetOf<String>()
        val folderCoverCache = mutableMapOf<String, String?>()
        try {
            scanDocumentTreeFast(treeUri, seenKeys, folderCoverCache) { track ->
                val excluded = excludedKeys.contains(track.id) ||
                    (track.filePath.isNotBlank() && excludedKeys.contains(track.filePath)) ||
                    (!track.contentUri.isNullOrBlank() && excludedKeys.contains(track.contentUri))
                if (!excluded && seenKeys.add(track.filePath.ifBlank { track.contentUri ?: track.id })) {
                    result.add(track)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("PhoneMusicScanner", "Single SAF tree scan error", e)
        }
        result
    }

    suspend fun parsePickedFiles(
        fileUris: List<Uri>,
        excludedKeys: Set<String> = emptySet()
    ): List<MobileAudioTrack> = withContext(Dispatchers.IO) {
        fileUris.mapNotNull { uri ->
            runCatching { parsePickedFileUriFast(uri) }.getOrNull()
        }.filter { track ->
            !excludedKeys.contains(track.id) &&
                (track.contentUri.isNullOrBlank() || !excludedKeys.contains(track.contentUri))
        }
    }

    private suspend fun scanMediaStoreAudio(
        folderCoverCache: MutableMap<String, String?>,
        onTrackFound: (MobileAudioTrack) -> Unit
    ) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        context.contentResolver.query(collection, projectionList.toTypedArray(), null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            if (idCol < 0) return
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else -1

            var rowCounter = 0
            while (cursor.moveToNext()) {
                if ((rowCounter++ and 0x1F) == 0) {
                    currentCoroutineContext().ensureActive()
                }
                val id = cursor.getLong(idCol)
                val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""
                val displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol) ?: "" else ""
                val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                val title = rawTitle?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.').takeIf { it.isNotBlank() }
                    ?: "Bài hát #$id"
                val artist = if (artistCol >= 0) cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Nghệ sĩ chưa rõ" else "Nghệ sĩ chưa rõ"
                val folderHint = relPath.trimEnd('/').substringAfterLast('/').ifBlank { "Điện thoại" }
                val album = if (albumCol >= 0) cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: folderHint else folderHint
                val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else -1L

                val combinedPath = "$path $relPath".lowercase(Locale.ROOT)
                val isSystemTone = (combinedPath.contains("/ringtones") ||
                    combinedPath.contains("/notifications") ||
                    combinedPath.contains("/alarms") ||
                    combinedPath.contains("/android/data")) && duration in 1..10_000L
                if (isSystemTone) continue

                val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    .ifBlank { displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) }

                if (supportedExtensions.contains(ext) || mime.startsWith("audio/")) {
                    val format = determineFormat(ext, mime)
                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val parent = if (path.isNotBlank()) path.substringBeforeLast('/', "") else relPath.trimEnd('/')
                    val albumArt = if (albumId > 0L) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else {
                        findCachedFolderCoverArt(parent, folderCoverCache)
                    }

                    onTrackFound(
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
    }

    private suspend fun scanMediaStoreDownloads(
        folderCoverCache: MutableMap<String, String?>,
        onTrackFound: (MobileAudioTrack) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.TITLE,
            MediaStore.Downloads.DATA,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.RELATIVE_PATH
        )

        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Downloads._ID)
            if (idCol < 0) return
            val nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndex(MediaStore.Downloads.TITLE)
            val dataCol = cursor.getColumnIndex(MediaStore.Downloads.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
            val mimeCol = cursor.getColumnIndex(MediaStore.Downloads.MIME_TYPE)
            val relCol = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)

            var rowCounter = 0
            while (cursor.moveToNext()) {
                if ((rowCounter++ and 0x1F) == 0) {
                    currentCoroutineContext().ensureActive()
                }
                val id = cursor.getLong(idCol)
                val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val relPath = if (relCol >= 0) cursor.getString(relCol) ?: "" else ""

                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    .ifBlank { path.substringAfterLast('.', "").lowercase(Locale.ROOT) }
                if (!supportedExtensions.contains(ext) && !mime.startsWith("audio/")) continue

                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                val title = rawTitle?.takeIf { it.isNotBlank() }
                    ?: name.substringBeforeLast('.').takeIf { it.isNotBlank() }
                    ?: "Bài hát #$id"
                val format = determineFormat(ext, mime)
                val parentDir = if (path.isNotBlank()) path.substringBeforeLast('/', "") else relPath.trimEnd('/')
                val folderLabel = parentDir.substringAfterLast('/').ifBlank { "Download" }

                onTrackFound(
                    MobileAudioTrack(
                        id = "dl_$id",
                        title = title,
                        artist = "Nghệ sĩ chưa rõ",
                        album = folderLabel,
                        durationMs = 0L,
                        filePath = path,
                        contentUri = contentUri,
                        format = format,
                        bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps",
                        fileSizeBytes = size,
                        parentDirectory = parentDir,
                        albumArtUri = findCachedFolderCoverArt(parentDir, folderCoverCache)
                    )
                )
            }
        }
    }

    private suspend fun scanDirectoryFast(
        dir: File,
        seenKeys: Set<String>,
        folderCoverCache: MutableMap<String, String?>,
        maxDepth: Int,
        currentDepth: Int = 0,
        onTrackFound: (MobileAudioTrack) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        if (currentDepth > maxDepth || !dir.canRead()) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            currentCoroutineContext().ensureActive()
            if (file.isDirectory) {
                val name = file.name
                if (!name.startsWith(".") && !name.equals("Android", ignoreCase = true)) {
                    scanDirectoryFast(file, seenKeys, folderCoverCache, maxDepth, currentDepth + 1, onTrackFound)
                }
            } else if (file.isFile) {
                val ext = file.extension.lowercase(Locale.ROOT)
                val absPath = file.absolutePath
                if (supportedExtensions.contains(ext) && !seenKeys.contains(absPath)) {
                    val format = ext.uppercase(Locale.ROOT)
                    val parentPath = file.parent ?: ""
                    onTrackFound(
                        MobileAudioTrack(
                            id = "file_${absPath.hashCode()}",
                            title = file.nameWithoutExtension,
                            artist = "Nghệ sĩ chưa rõ",
                            album = file.parentFile?.name ?: "Điện thoại",
                            durationMs = 0L,
                            filePath = absPath,
                            format = format,
                            bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps",
                            fileSizeBytes = file.length(),
                            parentDirectory = parentPath,
                            albumArtUri = findCachedFolderCoverArt(parentPath, folderCoverCache)
                        )
                    )
                }
            }
        }
    }

    private suspend fun scanDocumentTreeFast(
        treeUri: Uri,
        seenKeys: Set<String>,
        folderCoverCache: MutableMap<String, String?>,
        onTrackFound: (MobileAudioTrack) -> Unit
    ) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        if (rootDocId.startsWith("raw:")) {
            val rawDir = File(rootDocId.removePrefix("raw:"))
            if (rawDir.exists() && rawDir.isDirectory && rawDir.canRead()) {
                scanDirectoryFast(rawDir, seenKeys, folderCoverCache, maxDepth = 5, onTrackFound = onTrackFound)
                return
            }
        }
        val folderName = formatTreeDisplayName(treeUri)
        scanDocumentFolderFast(treeUri, rootDocId, folderName, seenKeys, depth = 0, maxDepth = 5, onTrackFound = onTrackFound)
    }

    private suspend fun scanDocumentFolderFast(
        treeUri: Uri,
        parentDocId: String,
        folderName: String,
        seenKeys: Set<String>,
        depth: Int,
        maxDepth: Int,
        onTrackFound: (MobileAudioTrack) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > maxDepth) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val docIdCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            if (docIdCol < 0) return
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val docId = cursor.getString(docIdCol) ?: continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) {
                        scanDocumentFolderFast(treeUri, docId, name, seenKeys, depth + 1, maxDepth, onTrackFound)
                    }
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (supportedExtensions.contains(ext) || mime.startsWith("audio/")) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val uriStr = docUri.toString()
                        if (!seenKeys.contains(uriStr)) {
                            val format = determineFormat(ext, mime)
                            onTrackFound(
                                MobileAudioTrack(
                                    id = "saf_${uriStr.hashCode()}",
                                    title = name.substringBeforeLast('.').ifBlank { name },
                                    artist = "Nghệ sĩ chưa rõ",
                                    album = folderName,
                                    durationMs = 0L,
                                    filePath = "",
                                    contentUri = uriStr,
                                    format = format,
                                    bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps",
                                    fileSizeBytes = size,
                                    parentDirectory = folderName,
                                    albumArtUri = null
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parsePickedFileUriFast(fileUri: Uri): MobileAudioTrack? {
        var displayName = fileUri.lastPathSegment?.substringAfterLast('/') ?: "Nhạc đã chọn"
        var size = 0L
        val mime = context.contentResolver.getType(fileUri) ?: ""
        try {
            context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
        }

        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (!supportedExtensions.contains(ext) && !mime.startsWith("audio/")) {
            return null
        }
        val uriStr = fileUri.toString()
        val format = determineFormat(ext, mime)
        return MobileAudioTrack(
            id = "saf_${uriStr.hashCode()}",
            title = displayName.substringBeforeLast('.').ifBlank { displayName },
            artist = "Nghệ sĩ chưa rõ",
            album = "Nhạc đã chọn",
            durationMs = 0L,
            filePath = "",
            contentUri = uriStr,
            format = format,
            bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps",
            fileSizeBytes = size,
            parentDirectory = "Nhạc đã chọn",
            albumArtUri = null
        )
    }

    private fun findCachedFolderCoverArt(
        folderPath: String,
        cache: MutableMap<String, String?>
    ): String? {
        if (folderPath.isBlank() || !folderPath.startsWith("/")) return null
        if (cache.containsKey(folderPath)) return cache[folderPath]

        var resolved: String? = null
        val folder = File(folderPath)
        if (folder.exists() && folder.isDirectory) {
            for (candidateName in coverFileNames) {
                val candidate = File(folder, candidateName)
                if (candidate.isFile && candidate.length() > 0L) {
                    resolved = candidate.absolutePath
                    break
                }
            }
        }
        cache[folderPath] = resolved
        return resolved
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

    companion object {
        fun formatTreeDisplayName(treeUri: Uri): String {
            return try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                when {
                    docId.startsWith("raw:") -> docId.removePrefix("raw:")
                    docId.contains(":") -> {
                        val afterColon = docId.substringAfter(":").trim('/')
                        if (afterColon.isBlank()) "Bộ nhớ gốc" else afterColon
                    }
                    else -> docId
                }
            } catch (_: Exception) {
                treeUri.lastPathSegment?.substringAfterLast(':') ?: "Thư mục đã chọn"
            }
        }
    }
}
