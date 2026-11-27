package com.example.data.sync

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

enum class ArchiveSubfolder(val dirName: String) {
    KEY_REGISTER("Key Register"),
    WEEKLY_MONTHLY("Weekly & Monthly Reports"),
    INSPECTIONS("Inspections"),
    BACKUPS("Backups")
}

data class ArchivedFileResult(
    val fileName: String,
    val subfolder: ArchiveSubfolder,
    val publicDisplayPath: String,
    val cacheFile: File,
    val shareUri: Uri,
    val mimeType: String,
    val sizeBytes: Long
)

data class LocalArchiveEntry(
    val fileName: String,
    val subfolderName: String,
    val filePath: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val mimeType: String
)

class LocalFolderArchiveManager(private val context: Context) {

    private val exportsCacheDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    private val inspectionPhotosDir: File
        get() = File(context.filesDir, "inspection_photos").apply { mkdirs() }

    suspend fun saveExportArtifact(
        subfolder: ArchiveSubfolder,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): ArchivedFileResult = withContext(Dispatchers.IO) {
        val safeName = fileName.replace(Regex("[/:*?\"<>|]"), "_")
        val subCacheDir = File(exportsCacheDir, subfolder.dirName).apply { mkdirs() }
        val cacheFile = File(subCacheDir, safeName)
        FileOutputStream(cacheFile).use { it.write(bytes) }

        // Keep an indexed copy in app-private Documents/Masaken Mesaimeer/<Subfolder> too
        val appDocsDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
            "Masaken Mesaimeer/${subfolder.dirName}"
        ).apply { mkdirs() }
        val appDocFile = File(appDocsDir, safeName)
        if (appDocFile.absolutePath != cacheFile.absolutePath) {
            FileOutputStream(appDocFile).use { it.write(bytes) }
        }

        val publicDisplayPath = saveToPublicDocumentsIfPossible(
            subfolder = subfolder,
            fileName = safeName,
            mimeType = mimeType,
            bytes = bytes,
            fallbackPath = appDocFile.absolutePath
        )

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        ArchivedFileResult(
            fileName = safeName,
            subfolder = subfolder,
            publicDisplayPath = publicDisplayPath,
            cacheFile = cacheFile,
            shareUri = shareUri,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong()
        )
    }

    private fun saveToPublicDocumentsIfPossible(
        subfolder: ArchiveSubfolder,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        fallbackPath: String
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return fallbackPath
        }
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/Masaken Mesaimeer/${subfolder.dirName}"
        val resolver = context.contentResolver
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values) ?: return fallbackPath
            resolver.openOutputStream(itemUri)?.use { out ->
                out.write(bytes)
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            "Documents/Masaken Mesaimeer/${subfolder.dirName}/$fileName"
        } catch (_: Exception) {
            fallbackPath
        }
    }

    suspend fun listCachedArchives(): List<LocalArchiveEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<LocalArchiveEntry>()
        ArchiveSubfolder.entries.forEach { sub ->
            val dir = File(exportsCacheDir, sub.dirName)
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val mime = if (file.name.endsWith(".json", ignoreCase = true)) {
                            "application/json"
                        } else {
                            MIME_XLSX
                        }
                        entries.add(
                            LocalArchiveEntry(
                                fileName = file.name,
                                subfolderName = sub.dirName,
                                filePath = file.absolutePath,
                                sizeBytes = file.length(),
                                modifiedAtMillis = file.lastModified(),
                                mimeType = mime
                            )
                        )
                    }
                }
            }
        }
        entries.sortedByDescending { it.modifiedAtMillis }
    }

    fun getShareUriForPath(cachePath: String): Uri? {
        val file = File(cachePath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun importPickedPhotoToLocal(uri: Uri, prefix: String = "insp"): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val scaled = scaleBitmap(decoded, maxDim = 1280)
            val outFile = File(inspectionPhotosDir, "${prefix}_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 84, fos)
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadScaledJpegForExcel(path: String, maxDim: Int = 720, quality: Int = 78): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext null
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            val scaled = scaleBitmap(decoded, maxDim = maxDim)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val MIME_JSON = "application/json"
    }
}
