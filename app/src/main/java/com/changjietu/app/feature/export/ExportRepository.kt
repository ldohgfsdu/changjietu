package com.changjietu.app.feature.export

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

class ExportRepository(private val context: Context) {

    fun saveAllToGallery(slices: List<SliceOutput>): Result<Int> = runCatching {
        var count = 0
        for (slice in slices) {
            if (saveOne(slice.file, slice.file.name)) {
                count++
            }
        }
        count
    }

    private fun saveOne(file: File, displayName: String): Boolean {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val pendingKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.IS_PENDING
        } else {
            null
        }
        val insertValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Changjietu")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, insertValues)
            ?: return false
        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out) }
            }
            if (pendingKey != null) {
                val successValues = ContentValues().apply { put(pendingKey, 0) }
                resolver.update(uri, successValues, null, null)
            }
            return true
        } catch (_: Exception) {
            // 清理 orphan pending row
            if (pendingKey != null) {
                resolver.delete(uri, pendingKey + "=1", null)
            }
            return false
        }
    }

    fun buildShareIntent(slices: List<SliceOutput>): Intent {
        val uris = ArrayList<Uri>()
        for (slice in slices) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                slice.file,
            )
            uris.add(uri)
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "长截图切片 ${slices.size} 张")
        }
    }
}
