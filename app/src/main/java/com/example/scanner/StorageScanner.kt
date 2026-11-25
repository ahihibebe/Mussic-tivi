package com.example.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class TvScanProgress(
    val tracks: List<AudioTrack>,
    val folders: List<AudioFolder>,
    val stageLabel: String,
    val latestTrackTitle: String?
)

class StorageScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "mid"
    )

    private val coverFileNames = listOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "album.jpg", "album.png", "front.jpg", "front.png"
    )

    data class ScanResult(
        val tracks: List<AudioTrack>,
        val folders: List<AudioFolder>
    )

    suspend fun scanAll(
        extraTreeUris: Set<Uri> = emptySet(),
        extraFileUris: Set<Uri> = emptySet(),
        extraLocalPaths: Set<String> = emptySet(),
        onProgress: ((TvScanProgress) -> Unit)? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<AudioTrack>()
        val seenKeys = mutableSetOf<String>()
        val seenFingerprints = mutableSetOf<String>()
        val folderCoverCache = mutableMapOf<String, String?>()
        var lastEmitTime = 0L

        fun emitProgress(stage: String, latestTitle: String?, force: Boolean = false) {
            if (onProgress == null) return
            val now = System.currentTimeMillis()
            if (force || now - lastEmitTime >= 150L) {
                lastEmitTime = now
                val snapshot = tracksList.toList()
                onProgress(
                    TvScanProgress(
                        tracks = snapshot,
                        folders = buildFolderHierarchy(snapshot),
                        stageLabel = stage,
                        latestTrackTitle = latestTitle
                    )
                )
            }
        }

        fun tryAddTrack(track: AudioTrack, stage: String) {
            val primaryKey = track.filePath.ifBlank { track.contentUri ?: track.id }
            val fileName = track.filePath.takeIf { it.isNotBlank() }?.let {
                it.substringAfterLast('/')
            } ?: track.title
            val fingerprint = "${fileName.lowercase(Locale.ROOT)}|${track.fileSizeBytes}"
            val isDuplicateFingerprint = track.fileSizeBytes > 0L && !seenFingerprints.add(fingerprint)
            if (seenKeys.add(primaryKey) && !isDuplicateFingerprint) {
                tracksList.add(track)
                emitProgress(stage, track.title, force = tracksList.size <= 8)
            }
        }

        // Clean up any leftover demo_music files from earlier versions
        try {
            val legacyDemoDir = File(context.filesDir, "demo_music")
            if (legacyDemoDir.exists()) {
                legacyDemoDir.deleteRecursively()
            }
        } catch (ignored: Exception) {}

        currentCoroutineContext().ensureActive()
        emitProgress("Đang quét nhanh MediaStore...", null, force = true)

        // 1. Fast SQLite query on MediaStore.Audio (without IS_MUSIC != 0 restriction)
        try {
            scanMediaStoreAudio(folderCoverCache) { track ->
                tryAddTrack(track, "Đang quét kho nhạc MediaStore...")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("StorageScanner", "Error querying MediaStore Audio", e)
        }

        currentCoroutineContext().ensureActive()

        // 2. Fast SQLite query on MediaStore.Downloads (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            emitProgress("Đang quét mục Download...", tracksList.lastOrNull()?.title, force = true)
            try {
                scanMediaStoreDownloads(folderCoverCache) { track ->
                    tryAddTrack(track, "Đang quét mục Download...")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("StorageScanner", "Error querying MediaStore Downloads", e)
            }
        }

        // 3. Direct fast filesystem scan on targeted device folders & USB mounts
        val candidateDirs = linkedSetOf<Pair<String, File>>()
        val wifiMusicDir = File(context.filesDir, "wifi_music").apply { mkdirs() }
        candidateDirs.add("Đang kiểm tra nhạc nhận qua Wi-Fi..." to wifiMusicDir)

        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                candidateDirs.add("Đang quét thư mục Download..." to it)
            }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let {
                candidateDirs.add("Đang quét thư mục Music..." to it)
            }
            Environment.getExternalStorageDirectory()?.let { root ->
                candidateDirs.add("Đang quét thư mục Download..." to File(root, "Download"))
                candidateDirs.add("Đang quét thư mục Downloads..." to File(root, "Downloads"))
                candidateDirs.add("Đang quét thư mục Music..." to File(root, "Music"))
                candidateDirs.add("Đang quét thư mục Documents..." to File(root, "Documents"))
                candidateDirs.add("Đang quét thư mục Recordings..." to File(root, "Recordings"))
                candidateDirs.add("Đang quét thư mục Zalo..." to File(root, "Zalo"))
                candidateDirs.add("Đang quét thư mục Telegram..." to File(root, "Telegram"))
            }

            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                storageRoot.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.canRead() && !file.name.equals("self", ignoreCase = true) && !file.name.equals("emulated", ignoreCase = true)) {
                        candidateDirs.add("Đang quét USB / Thẻ nhớ (${file.name})..." to file)
                    }
                }
            }
            for (localPath in extraLocalPaths) {
                val f = File(localPath)
                if (f.exists() && f.isDirectory) {
                    candidateDirs.add("Đang quét thư mục (${f.name})..." to f)
                }
            }
        } catch (e: Exception) {
            Log.e("StorageScanner", "Error checking storage roots", e)
        }

        val visitedDirs = mutableSetOf<String>()
        for ((stageLabel, dir) in candidateDirs) {
            currentCoroutineContext().ensureActive()
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (visitedDirs.add(canonical) && dir.exists() && dir.canRead()) {
                emitProgress(stageLabel, tracksList.lastOrNull()?.title, force = true)
                scanDirectoryFast(dir, seenKeys, folderCoverCache, maxDepth = 4) { found ->
                    tryAddTrack(found, stageLabel)
                }
            }
        }

        // 4. Scan persisted SAF folders selected via "Mở thư mục"
        for (treeUri in extraTreeUris) {
            currentCoroutineContext().ensureActive()
            emitProgress("Đang quét thư mục đã chọn...", tracksList.lastOrNull()?.title, force = true)
            try {
                scanDocumentTreeFast(treeUri, seenKeys, folderCoverCache) { found ->
                    tryAddTrack(found, "Đang quét thư mục đã chọn...")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("StorageScanner", "Error scanning SAF tree $treeUri", e)
            }
        }

        // 5. Scan picked SAF individual files
        if (extraFileUris.isNotEmpty()) {
            emitProgress("Đang nạp file nhạc đã chọn...", tracksList.lastOrNull()?.title, force = true)
            for (fileUri in extraFileUris) {
                currentCoroutineContext().ensureActive()
                try {
                    parsePickedFileUriFast(fileUri)?.let { found ->
                        tryAddTrack(found, "Đang nạp file nhạc đã chọn...")
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("StorageScanner", "Error scanning SAF file $fileUri", e)
                }
            }
        }

        val finalTracks = tracksList.sortedBy { it.title.lowercase(Locale.ROOT) }
        val finalFolders = buildFolderHierarchy(finalTracks)
        onProgress?.invoke(
            TvScanProgress(
                tracks = finalTracks,
                folders = finalFolders,
                stageLabel = "Quét hoàn tất",
                latestTrackTitle = null
            )
        )
        ScanResult(finalTracks, finalFolders)
    }

    suspend fun scanCustomDirectory(directoryPath: String): List<AudioTrack> = withContext(Dispatchers.IO) {
        val target = File(directoryPath)
        val tracks = mutableListOf<AudioTrack>()
        val seen = mutableSetOf<String>()
        val cache = mutableMapOf<String, String?>()
        if (target.exists() && target.isDirectory) {
            scanDirectoryFast(target, seen, cache, maxDepth = 5) { tracks.add(it) }
        }
        tracks
    }

    private suspend fun scanMediaStoreAudio(
        folderCoverCache: MutableMap<String, String?>,
        onTrackFound: (AudioTrack) -> Unit
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
                val folderHint = relPath.trimEnd('/').substringAfterLast('/').ifBlank { "Bộ nhớ trong" }
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
                    val parent = if (path.isNotBlank()) path.substringBeforeLast('/', "") else relPath.trimEnd('/').ifBlank { "Bộ nhớ trong" }
                    val albumArt = if (albumId > 0L) {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else {
                        findCachedFolderCoverArt(parent, folderCoverCache)
                    }

                    onTrackFound(
                        AudioTrack(
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
        onTrackFound: (AudioTrack) -> Unit
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
                val parentDir = if (path.isNotBlank()) path.substringBeforeLast('/', "") else relPath.trimEnd('/').ifBlank { "Download" }
                val folderLabel = parentDir.substringAfterLast('/').ifBlank { "Download" }

                onTrackFound(
                    AudioTrack(
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
        onTrackFound: (AudioTrack) -> Unit
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
                    val isLossless = format == "FLAC" || format == "WAV"
                    val parentPath = file.parent ?: ""
                    onTrackFound(
                        AudioTrack(
                            id = "file_${absPath.hashCode()}",
                            title = file.nameWithoutExtension,
                            artist = "Nghệ sĩ chưa rõ",
                            album = file.parentFile?.name ?: "Bộ nhớ máy",
                            durationMs = 0L,
                            filePath = absPath,
                            format = format,
                            bitrate = if (isLossless) "Lossless" else "320 kbps",
                            sampleRate = if (isLossless) "96.0 kHz" else "44.1 kHz",
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
        onTrackFound: (AudioTrack) -> Unit
    ) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        if (rootDocId.startsWith("raw:")) {
            val rawDir = File(rootDocId.removePrefix("raw:"))
            if (rawDir.exists() && rawDir.isDirectory && rawDir.canRead()) {
                scanDirectoryFast(rawDir, seenKeys, folderCoverCache, maxDepth = 5, onTrackFound = onTrackFound)
                return
            }
        }
        val folderName = rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Thư mục đã chọn" }
        scanDocumentFolderFast(treeUri, rootDocId, folderName, seenKeys, depth = 0, maxDepth = 5, onTrackFound = onTrackFound)
    }

    private suspend fun scanDocumentFolderFast(
        treeUri: Uri,
        parentDocId: String,
        folderName: String,
        seenKeys: Set<String>,
        depth: Int,
        maxDepth: Int,
        onTrackFound: (AudioTrack) -> Unit
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
                            val isLossless = format == "FLAC" || format == "WAV"
                            onTrackFound(
                                AudioTrack(
                                    id = "saf_${uriStr.hashCode()}",
                                    title = name.substringBeforeLast('.').ifBlank { name },
                                    artist = "Nghệ sĩ chưa rõ",
                                    album = folderName,
                                    durationMs = 0L,
                                    filePath = "",
                                    contentUri = uriStr,
                                    format = format,
                                    bitrate = if (isLossless) "Lossless" else "320 kbps",
                                    sampleRate = if (isLossless) "96.0 kHz" else "44.1 kHz",
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

    private fun parsePickedFileUriFast(fileUri: Uri): AudioTrack? {
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
        val isLossless = format == "FLAC" || format == "WAV"
        return AudioTrack(
            id = "saf_${uriStr.hashCode()}",
            title = displayName.substringBeforeLast('.').ifBlank { displayName },
            artist = "Nghệ sĩ chưa rõ",
            album = "Download / Đã chọn",
            durationMs = 0L,
            filePath = "",
            contentUri = uriStr,
            format = format,
            bitrate = if (isLossless) "Lossless" else "320 kbps",
            sampleRate = if (isLossless) "96.0 kHz" else "44.1 kHz",
            fileSizeBytes = size,
            parentDirectory = "Download / Đã chọn",
            albumArtUri = null
        )
    }

    private fun findCachedFolderCoverArt(
        folderPath: String,
        cache: MutableMap<String, String?>
    ): String? {
        if (folderPath.isBlank() || !folderPath.startsWith("/")) return null
        if (cache.containsKey(folderPath)) return cache[folderPath]

        val folder = File(folderPath)
        var resolved: String? = null
        if (folder.exists() && folder.isDirectory && folder.canRead()) {
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

    private fun buildFolderHierarchy(tracks: List<AudioTrack>): List<AudioFolder> {
        val folderMap = mutableMapOf<String, MutableList<AudioTrack>>()
        for (track in tracks) {
            val dir = track.parentDirectory.ifEmpty { "Bộ nhớ trong" }
            folderMap.getOrPut(dir) { mutableListOf() }.add(track)
        }

        val wifiDirPath = File(context.filesDir, "wifi_music").absolutePath
        return folderMap.map { (path, trackList) ->
            val file = if (path.startsWith("/")) File(path) else null
            val folderName = when {
                path == wifiDirPath -> "Nhạc từ Wi-Fi"
                file != null && file.name.isNotEmpty() -> file.name
                else -> path
            }

            AudioFolder(
                path = path,
                name = folderName,
                trackCount = trackList.size,
                subfolderCount = 0,
                parentPath = file?.parent,
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
            ext.equals("m4a", ignoreCase = true) || mime.contains("mp4") -> "M4A"
            ext.equals("wma", ignoreCase = true) -> "WMA"
            ext.equals("mid", ignoreCase = true) -> "MIDI"
            else -> "MP3"
        }
    }
}
