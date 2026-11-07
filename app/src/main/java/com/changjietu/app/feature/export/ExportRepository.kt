package com.changjietu.app.feature.export

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.changjietu.app.image.processing.SliceOutput
import java.io.File
import java.io.FileInputStream
import java.io.IOException

data class SaveSummary(
    val saved: Int,
    val failed: Int,
) {
    val total: Int get() = saved + failed
}

data class SavedZip(
    val displayName: String,
    val locationLabel: String,
    val file: File,
)

/**
 * 导出：相册（Pictures/changjietu）与下载目录（Download/changjietu）。
 *
 * - API 29+ 走 MediaStore（IS_PENDING 清理孤儿行）。
 * - API 26–28 直接写公共目录，需要调用方先取得 WRITE_EXTERNAL_STORAGE。
 * - 分享统一用 FileProvider，不暴露 file:// URI。
 */
class ExportRepository(private val context: Context) {

    fun saveToGallery(
        slices: List<SliceOutput>,
        albumName: String = ALBUM_NAME,
    ): Result<SaveSummary> = runCatching {
        require(slices.isNotEmpty()) { "no slices" }
        var saved = 0
        var failed = 0
        for (slice in OrderedSlices.ascending(slices)) {
            if (saveOneToGallery(slice.file, slice.file.name, albumName)) {
                saved++
            } else {
                failed++
            }
        }
        SaveSummary(saved = saved, failed = failed)
    }

    private fun saveOneToGallery(file: File, displayName: String, albumName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                displayName = displayName,
                mimeType = "image/jpeg",
                relativeDir = Environment.DIRECTORY_PICTURES + "/" + albumName,
                source = file,
            ).isSuccess
        } else {
            saveToPublicDir(
                directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    albumName,
                ),
                displayName = displayName,
                source = file,
                scanAsImage = true,
            ).isSuccess
        }
    }

    fun saveZipToDownloads(
        zipFile: File,
        displayName: String,
        albumName: String = ALBUM_NAME,
    ): Result<SavedZip> = runCatching {
        require(zipFile.isFile && zipFile.length() > 0) { "zip file missing" }
        val safeName = if (displayName.endsWith(".zip", ignoreCase = true)) {
            displayName
        } else {
            "$displayName.zip"
        }
        val labelBase = Environment.DIRECTORY_DOWNLOADS + "/" + albumName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = saveToMediaStore(
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                displayName = safeName,
                mimeType = "application/zip",
                relativeDir = labelBase,
                source = zipFile,
            ).getOrThrow()
            SavedZip(
                displayName = safeName,
                locationLabel = "$labelBase/$safeName",
                file = zipFile,
            )
        } else {
            val savedFile = saveToPublicDir(
                directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    albumName,
                ),
                displayName = safeName,
                source = zipFile,
                scanAsImage = false,
            ).getOrThrow()
            // file 统一回填缓存 zip：公共 Downloads 副本不在 FileProvider 的 file_paths 覆盖范围内，
            // 拿它去 buildZipShareIntent 会在 API 26–28 上抛异常。展示名仍用落盘后的真实文件名。
            SavedZip(
                displayName = savedFile.name,
                locationLabel = "$labelBase/${savedFile.name}",
                file = zipFile,
            )
        }
    }

    private fun saveToMediaStore(
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativeDir: String,
        source: File,
    ): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed for $displayName")
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                FileInputStream(source).use { input -> input.copyTo(out) }
            } ?: throw IOException("MediaStore openOutputStream returned null")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri
        } catch (e: Exception) {
            runCatching {
                resolver.delete(
                    uri,
                    MediaStore.MediaColumns.IS_PENDING + "=1",
                    null,
                )
            }
            throw e
        }
    }

    private fun saveToPublicDir(
        directory: File,
        displayName: String,
        source: File,
        scanAsImage: Boolean,
    ): Result<File> = runCatching {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("cannot create ${directory.absolutePath}")
        }
        val target = uniqueFile(directory, displayName)
        try {
            FileInputStream(source).use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        if (scanAsImage) {
            runCatching {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.absolutePath),
                    arrayOf("image/jpeg"),
                    null,
                )
            }
        }
        target
    }

    private fun uniqueFile(directory: File, displayName: String): File {
        val first = File(directory, displayName)
        if (!first.exists()) return first
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        var i = 2
        while (true) {
            val candidate = File(directory, "${base}_$i$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    fun buildShareIntent(slices: List<SliceOutput>): Intent {
        require(slices.isNotEmpty()) { "no slices to share" }
        val uris = OrderedSlices.ascending(slices).map { slice ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                slice.file,
            )
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("长截图切片", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            putExtra(Intent.EXTRA_SUBJECT, "长截图切片 ${slices.size} 张")
        }
    }

    fun buildZipShareIntent(zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("长截图 ZIP", uri)
            putExtra(Intent.EXTRA_SUBJECT, "长截图切片压缩包")
        }
    }

    companion object {
        const val ALBUM_NAME = "changjietu"
    }
}
