package com.example.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
        onProgress: ((TvScanProgress) -> Unit)? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        val tracksList = mutableListOf<AudioTrack>()
        val seenKeys = mutableSetOf<String>()
        val seenFingerprints = mutableSetOf<String>()
        var lastEmitTime = 0L

        fun emitProgress(stage: String, latestTitle: String?, force: Boolean = false) {
            if (onProgress == null) return
            val now = System.currentTimeMillis()
            if (force || now - lastEmitTime >= 120L) {
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
            val primaryKey = track.filePath.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { File(path).canonicalPath }.getOrDefault(path)
            } ?: track.contentUri ?: track.id

            val fileName = track.filePath.takeIf { it.isNotBlank() }?.let { File(it).name } ?: track.title
            val fingerprint = "${fileName.lowercase(Locale.ROOT)}|${track.fileSizeBytes}"
            val isDuplicateFingerprint = track.fileSizeBytes > 0L && !seenFingerprints.add(fingerprint)
            if (seenKeys.add(primaryKey) && !isDuplicateFingerprint) {
                tracksList.add(track)
                emitProgress(stage, track.title, force = tracksList.size <= 5)
            }
        }

        // Clean up any leftover demo_music files from earlier versions
        try {
            val legacyDemoDir = File(context.filesDir, "demo_music")
            if (legacyDemoDir.exists()) {
                legacyDemoDir.deleteRecursively()
            }
        } catch (ignored: Exception) {}

        emitProgress("Đang quét kho nhạc MediaStore...", null, force = true)
        triggerMediaScanForCommonDirs()

        // 1. Scan MediaStore.Audio (without IS_MUSIC != 0 restriction so Download/ files are included)
        try {
            scanMediaStoreAudio { track ->
                tryAddTrack(track, "Đang quét kho nhạc MediaStore...")
            }
        } catch (e: Exception) {
            Log.e("StorageScanner", "Error querying MediaStore Audio", e)
        }
        emitProgress("Đang quét mục Download & Tệp âm thanh...", tracksList.lastOrNull()?.title, force = true)

        // 2. Supplement with MediaStore.Downloads & MediaStore.Files
        try {
            scanMediaStoreDownloadsAndFiles { track ->
                tryAddTrack(track, "Đang quét MediaStore Download...")
            }
        } catch (e: Exception) {
            Log.e("StorageScanner", "Error querying MediaStore Downloads/Files", e)
        }

        // 3. Scan targeted device storage folders & external mounts (USB on TV)
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
        } catch (e: Exception) {
            Log.e("StorageScanner", "Error checking storage roots", e)
        }

        val visitedDirs = mutableSetOf<String>()
        for ((stageLabel, dir) in candidateDirs) {
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (visitedDirs.add(canonical) && dir.exists() && dir.canRead()) {
                emitProgress(stageLabel, tracksList.lastOrNull()?.title, force = true)
                val dirKeys = mutableSetOf<String>()
                scanDirectoryRecursively(dir, dirKeys, maxDepth = 5) { found ->
                    tryAddTrack(found, stageLabel)
                }
            }
        }

        // 4. Scan persisted SAF folders selected via "Mở thư mục khác"
        extraTreeUris.forEach { treeUri ->
            emitProgress("Đang quét thư mục đã chọn...", tracksList.lastOrNull()?.title, force = true)
            try {
                val safKeys = mutableSetOf<String>()
                scanDocumentTree(treeUri, safKeys) { found ->
                    tryAddTrack(found, "Đang quét thư mục đã chọn...")
                }
            } catch (e: Exception) {
                Log.e("StorageScanner", "Error scanning SAF tree $treeUri", e)
            }
        }

        // 5. Scan picked SAF individual files
        if (extraFileUris.isNotEmpty()) {
            emitProgress("Đang nạp file nhạc đã chọn...", tracksList.lastOrNull()?.title, force = true)
            extraFileUris.forEach { fileUri ->
                try {
                    parsePickedFileUri(fileUri)?.let { found ->
                        tryAddTrack(found, "Đang nạp file nhạc đã chọn...")
                    }
                } catch (e: Exception) {
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
        if (target.exists() && target.isDirectory) {
            scanDirectoryRecursively(target, seen, maxDepth = 5) { tracks.add(it) }
        }
        tracks
    }

    private fun triggerMediaScanForCommonDirs() {
        try {
            val toScan = mutableListOf<String>()
            val dirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            )
            dirs.forEach { dir ->
                if (dir.exists() && dir.canRead()) {
                    dir.listFiles()?.forEach { child ->
                        if (child.isFile && supportedExtensions.contains(child.extension.lowercase(Locale.ROOT))) {
                            toScan.add(child.absolutePath)
                        }
                    }
                }
            }
            if (toScan.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, toScan.toTypedArray(), null, null)
            }
        } catch (_: Exception) {
        }
    }

    private fun scanMediaStoreAudio(onTrackFound: (AudioTrack) -> Unit) {
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

            while (cursor.moveToNext()) {
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

                val file = if (path.isNotEmpty()) File(path) else null
                val ext = file?.extension?.lowercase(Locale.ROOT)
                    ?.ifBlank { displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) }
                    ?: displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

                if (supportedExtensions.contains(ext) || mime.startsWith("audio/")) {
                    val format = determineFormat(ext, mime)
                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val parent = file?.parent ?: relPath.trimEnd('/').ifBlank { "Bộ nhớ trong" }
                    val albumArt = fastResolveMediaStoreOrFolderArt(albumId, file, contentUri)

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

    private fun scanMediaStoreDownloadsAndFiles(onTrackFound: (AudioTrack) -> Unit) {
        val urisToQuery = mutableListOf<Pair<Uri, String?>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            urisToQuery.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI to null)
        }
        val audioSelection = buildString {
            append("${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'audio/%'")
            supportedExtensions.forEach { ext ->
                append(" OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.$ext'")
            }
        }
        urisToQuery.add(MediaStore.Files.getContentUri("external") to audioSelection)

        for ((collection, selection) in urisToQuery) {
            try {
                val projectionList = mutableListOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.TITLE,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.MIME_TYPE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    projectionList.add(MediaStore.MediaColumns.RELATIVE_PATH)
                }

                val cursor = try {
                    context.contentResolver.query(collection, projectionList.toTypedArray(), selection, null, null)
                } catch (e: Exception) {
                    val fallbackSel = if (selection != null) {
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO} OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'audio/%'"
                    } else null
                    context.contentResolver.query(collection, projectionList.toTypedArray(), fallbackSel, null, null)
                }

                cursor?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    if (idCol < 0) return@use
                    val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val titleCol = c.getColumnIndex(MediaStore.Files.FileColumns.TITLE)
                    val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val mimeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                        val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                        val mime = if (mimeCol >= 0) c.getString(mimeCol) ?: "" else ""
                        val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                        val relPath = if (relCol >= 0) c.getString(relCol) ?: "" else ""

                        val extFromName = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val extFromPath = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val ext = extFromName.ifBlank { extFromPath }

                        if (!supportedExtensions.contains(ext) && !mime.startsWith("audio/")) continue

                        val contentUri = ContentUris.withAppendedId(collection, id).toString()
                        val file = if (path.isNotBlank()) File(path) else null
                        val rawTitle = if (titleCol >= 0) c.getString(titleCol) else null
                        val title = rawTitle?.takeIf { it.isNotBlank() }
                            ?: name.substringBeforeLast('.').takeIf { it.isNotBlank() }
                            ?: file?.nameWithoutExtension
                            ?: "Bài hát #$id"
                        val format = determineFormat(ext, mime)
                        val folderLabel = file?.parentFile?.name
                            ?: relPath.trimEnd('/').substringAfterLast('/').ifBlank { "Download" }

                        onTrackFound(
                            AudioTrack(
                                id = "dl_${collection.hashCode()}_$id",
                                title = title,
                                artist = "Nghệ sĩ chưa rõ",
                                album = folderLabel,
                                durationMs = 0L,
                                filePath = path,
                                contentUri = contentUri,
                                format = format,
                                bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps",
                                fileSizeBytes = size,
                                parentDirectory = file?.parent ?: relPath.trimEnd('/').ifBlank { "Download" },
                                albumArtUri = findFolderCoverArt(file?.parentFile)
                            )
                        )
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        seenPaths: MutableSet<String>,
        maxDepth: Int,
        currentDepth: Int = 0,
        onTrackFound: (AudioTrack) -> Unit
    ) {
        if (currentDepth > maxDepth || !dir.canRead()) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".") && !file.name.equals("Android", ignoreCase = true)) {
                scanDirectoryRecursively(file, seenPaths, maxDepth, currentDepth + 1, onTrackFound)
            } else if (file.isFile) {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (supportedExtensions.contains(ext) && seenPaths.add(file.absolutePath)) {
                    onTrackFound(parseTrackMetadata(file, ext))
                }
            }
        }
    }

    private fun scanDocumentTree(
        treeUri: Uri,
        seenKeys: MutableSet<String>,
        onTrackFound: (AudioTrack) -> Unit
    ) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        if (rootDocId.startsWith("raw:")) {
            val rawDir = File(rootDocId.removePrefix("raw:"))
            if (rawDir.exists() && rawDir.isDirectory && rawDir.canRead()) {
                scanDirectoryRecursively(rawDir, seenKeys, maxDepth = 5, onTrackFound = onTrackFound)
            }
        }
        val folderName = rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Thư mục đã chọn" }
        scanDocumentFolder(treeUri, rootDocId, folderName, seenKeys, depth = 0, maxDepth = 5, onTrackFound = onTrackFound)
    }

    private fun scanDocumentFolder(
        treeUri: Uri,
        parentDocId: String,
        folderName: String,
        seenKeys: MutableSet<String>,
        depth: Int,
        maxDepth: Int,
        onTrackFound: (AudioTrack) -> Unit
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
            val docIdCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            if (docIdCol < 0) return
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(docIdCol) ?: continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) {
                        scanDocumentFolder(treeUri, docId, name, seenKeys, depth + 1, maxDepth, onTrackFound)
                    }
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (supportedExtensions.contains(ext) || mime.startsWith("audio/")) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val uriStr = docUri.toString()
                        if (seenKeys.add(uriStr)) {
                            onTrackFound(parseSafTrackMetadata(docUri, name, ext, mime, size, folderName))
                        }
                    }
                }
            }
        }
    }

    private fun parsePickedFileUri(fileUri: Uri): AudioTrack? {
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
        return parseSafTrackMetadata(fileUri, displayName, ext, mime, size, "Download / Đã chọn")
    }

    private fun parseSafTrackMetadata(
        docUri: Uri,
        displayName: String,
        ext: String,
        mime: String,
        size: Long,
        folderName: String
    ): AudioTrack {
        val format = determineFormat(ext, mime)
        var title = displayName.substringBeforeLast('.').ifBlank { displayName }
        var artist = "Nghệ sĩ chưa rõ"
        var album = folderName
        var durationMs = 0L
        var bitrate = if (format == "FLAC" || format == "WAV") "Lossless" else "320 kbps"
        var albumArtUri: String? = null

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
            albumArtUri = cacheEmbeddedPicture(docUri.toString(), retriever.embeddedPicture)
        } catch (ignored: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        return AudioTrack(
            id = "saf_${docUri.toString().hashCode()}",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            filePath = "",
            contentUri = docUri.toString(),
            format = format,
            bitrate = bitrate,
            sampleRate = if (format == "FLAC" || format == "WAV") "96.0 kHz" else "44.1 kHz",
            fileSizeBytes = size,
            parentDirectory = folderName,
            albumArtUri = albumArtUri
        )
    }

    private fun parseTrackMetadata(file: File, ext: String): AudioTrack {
        val format = ext.uppercase(Locale.ROOT)
        var title = file.nameWithoutExtension
        var artist = "Nghệ sĩ chưa rõ"
        var album = file.parentFile?.name ?: "Bộ nhớ máy"
        var durationMs = 0L
        var bitrate = "320 kbps"
        var sampleRate = "44.1 kHz"
        var albumArtUri: String? = null

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

            albumArtUri = cacheEmbeddedPicture(file.absolutePath, retriever.embeddedPicture)
        } catch (e: Exception) {
            // Fallback gracefully to filename
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        if (albumArtUri == null) {
            albumArtUri = findFolderCoverArt(file.parentFile)
        }

        if (format == "FLAC" || format == "WAV") {
            bitrate = "Lossless"
            sampleRate = "96.0 kHz"
        }

        return AudioTrack(
            id = "file_${file.absolutePath.hashCode()}",
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
            albumArtUri = albumArtUri
        )
    }

    private fun fastResolveMediaStoreOrFolderArt(albumId: Long, file: File?, contentUri: String): String? {
        val cacheKey = file?.absolutePath ?: contentUri
        val cacheFile = File(getAlbumArtCacheDir(), "${cacheKey.hashCode()}.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return cacheFile.absolutePath
        }

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
        if (folder == null || !folder.exists() || !folder.isDirectory || !folder.canRead()) return null
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

            val subfolders = file?.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.size ?: 0

            AudioFolder(
                path = path,
                name = folderName,
                trackCount = trackList.size,
                subfolderCount = subfolders,
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
