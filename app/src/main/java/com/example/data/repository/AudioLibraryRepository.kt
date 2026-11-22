package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.audio.converter.FormatStringEngine
import com.example.audio.tagging.AudioTagEngine
import com.example.audio.tagging.BinaryUtils
import com.example.audio.tagging.SampleAudioGenerator
import com.example.model.AudioFormat
import com.example.model.AudioTrackItem
import com.example.model.CoverArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

class AudioLibraryRepository(private val context: Context) {

    private val supportedExtensions = setOf("mp3", "flac", "m4a", "mp4", "aac", "ogg", "oga", "wav", "wave")

    fun workspaceDirectory(): File = File(context.filesDir, "sample_workspace")

    suspend fun loadFolderTree(treeUri: Uri, includeSubfolders: Boolean): Result<Pair<String, List<AudioTrackItem>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                val root = DocumentFile.fromTreeUri(context, treeUri)
                    ?: error("Unable to access selected folder")
                val folderName = root.name ?: "Music Folder"
                val collected = mutableListOf<AudioTrackItem>()
                scanDocumentFolder(root, folderName, includeSubfolders, collected)
                folderName to collected
            }
        }

    private suspend fun scanDocumentFolder(
        dir: DocumentFile,
        dirLabel: String,
        recursive: Boolean,
        dest: MutableList<AudioTrackItem>
    ) {
        val children = dir.listFiles()
        for (doc in children) {
            if (doc.isDirectory) {
                if (recursive) {
                    scanDocumentFolder(doc, doc.name ?: dirLabel, true, dest)
                }
            } else if (doc.isFile) {
                val name = doc.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supportedExtensions) {
                    runCatching {
                        AudioTagEngine.readTrackFromUri(
                            context = context,
                            uri = doc.uri,
                            fileName = name,
                            directoryLabel = dirLabel,
                            fileSize = doc.length(),
                            lastModified = doc.lastModified()
                        )
                    }.onSuccess { dest.add(it) }
                }
            }
        }
    }

    suspend fun loadPickedUris(uris: List<Uri>): Result<List<AudioTrackItem>> = withContext(Dispatchers.IO) {
        runCatching {
            uris.mapNotNull { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                val (name, size) = queryDisplayNameAndSize(uri)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in supportedExtensions) return@mapNotNull null
                AudioTagEngine.readTrackFromUri(
                    context = context,
                    uri = uri,
                    fileName = name,
                    directoryLabel = "Picked Files",
                    fileSize = size
                )
            }
        }
    }

    suspend fun createAndLoadSampleWorkspace(): Result<List<AudioTrackItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = workspaceDirectory()
            val files = SampleAudioGenerator.createSampleWorkspaceFiles(dir)
            files.map { file ->
                AudioTagEngine.readTrackFromFile(file, "Sample Workspace")
            }
        }
    }

    suspend fun loadLocalDirectory(dir: File, label: String): Result<List<AudioTrackItem>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!dir.exists()) return@runCatching emptyList()
            dir.listFiles()
                ?.filter { it.isFile && AudioFormat.fromFilename(it.name) != AudioFormat.UNKNOWN }
                ?.sortedBy { it.name }
                ?.map { AudioTagEngine.readTrackFromFile(it, label) }
                ?: emptyList()
        }
    }

    suspend fun saveTrack(
        track: AudioTrackItem,
        id3v2Version: Int,
        writeId3v1: Boolean
    ): Result<AudioTrackItem> = AudioTagEngine.writeTrack(context, track, id3v2Version, writeId3v1)

    suspend fun stripTrackTags(track: AudioTrackItem): Result<AudioTrackItem> = withContext(Dispatchers.IO) {
        runCatching {
            val format = track.techInfo.format
            if (track.filePath != null) {
                val file = File(track.filePath)
                val stripped = AudioTagEngine.stripTags(file.readBytes(), format)
                file.writeBytes(stripped)
                AudioTagEngine.readTrackFromFile(file, track.directoryName)
            } else if (track.uri != null) {
                val original = context.contentResolver.openInputStream(track.uri)?.use { it.readBytes() }
                    ?: error("Unable to read stream")
                val stripped = AudioTagEngine.stripTags(original, format)
                val out = context.contentResolver.openOutputStream(track.uri, "wt")
                    ?: context.contentResolver.openOutputStream(track.uri)
                    ?: error("Unable to open output stream")
                out.use { it.write(stripped) }
                AudioTagEngine.readTrackFromUri(
                    context = context,
                    uri = track.uri,
                    fileName = track.fileName,
                    directoryLabel = track.directoryName,
                    fileSize = stripped.size.toLong(),
                    lastModified = System.currentTimeMillis()
                )
            } else {
                error("Missing file reference")
            }
        }
    }

    suspend fun renameTrackFile(track: AudioTrackItem, newFileName: String): Result<AudioTrackItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = FormatStringEngine.sanitizeFilename(newFileName)
                if (sanitized.isBlank() || sanitized == track.fileName) return@runCatching track

                if (track.filePath != null) {
                    val oldFile = File(track.filePath)
                    val targetFile = File(oldFile.parentFile, sanitized)
                    if (targetFile.exists()) error("Target file already exists: $sanitized")
                    if (!oldFile.renameTo(targetFile)) error("Could not rename ${oldFile.name}")
                    AudioTagEngine.readTrackFromFile(targetFile, track.directoryName)
                } else if (track.uri != null) {
                    val newUri = DocumentsContract.renameDocument(context.contentResolver, track.uri, sanitized)
                        ?: error("Document provider refused rename to $sanitized")
                    AudioTagEngine.readTrackFromUri(
                        context = context,
                        uri = newUri,
                        fileName = sanitized,
                        directoryLabel = track.directoryName,
                        fileSize = track.techInfo.fileSizeBytes,
                        lastModified = System.currentTimeMillis()
                    )
                } else {
                    error("No file reference")
                }
            }
        }

    suspend fun loadCoverArtFromUri(uri: Uri): Result<CoverArt> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read selected image")
            if (bytes.isEmpty()) error("Selected image is empty")
            val mime = context.contentResolver.getType(uri) ?: BinaryUtils.detectImageMimeType(bytes)
            val (w, h) = BinaryUtils.decodeImageDimensions(bytes)
            CoverArt(bytes = bytes, mimeType = mime, pictureType = 3, description = "Front Cover", width = w, height = h)
        }
    }

    suspend fun writeTextDocument(uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open target document")
            out.use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
        }
    }

    suspend fun writeBytesDocument(uri: Uri, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open target document")
            out.use { it.write(bytes) }
        }
    }

    suspend fun readTextDocument(uri: Uri): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use {
                it.readLines()
            } ?: emptyList()
        }
    }

    fun buildM3u8Playlist(tracks: List<AudioTrackItem>): String {
        val sb = StringBuilder("#EXTM3U\n")
        tracks.forEach { t ->
            val sec = (t.techInfo.durationMs / 1000L).coerceAtLeast(-1L)
            val artist = t.tags.artist.ifBlank { "Unknown Artist" }
            val title = t.tags.title.ifBlank { t.baseName }
            sb.append("#EXTINF:$sec,$artist - $title\n")
            sb.append("${t.fileName}\n")
        }
        return sb.toString()
    }

    fun buildCsvExport(tracks: List<AudioTrackItem>): String {
        val header = "Filename;Title;Artist;Album;AlbumArtist;Track;Disc;Year;Genre;Comment;Bitrate"
        val rows = tracks.map { t ->
            listOf(
                t.fileName, t.tags.title, t.tags.artist, t.tags.album, t.tags.albumArtist,
                t.tags.track, t.tags.discNumber, t.tags.year, t.tags.genre, t.tags.comment,
                t.techInfo.bitrateKbps.toString()
            ).joinToString(";") { it.replace(";", ",") }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "track.mp3"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }
}
