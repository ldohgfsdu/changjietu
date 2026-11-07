package com.changjietu.app.feature.export

import com.changjietu.app.image.processing.SliceOutput
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把选中的切片打包成 ZIP。纯 JVM 实现，使用平台 `java.util.zip`，不引入新依赖。
 *
 * 先写 `.part` 临时文件，成功后再替换目标文件，避免失败时留下半截 ZIP。
 */
object SliceZipper {

    fun zip(slices: List<SliceOutput>, output: File): Int {
        require(slices.isNotEmpty()) { "no slices to zip" }
        output.parentFile?.mkdirs()
        val part = File(output.parentFile, output.name + ".part")
        try {
            var count = 0
            val usedNames = HashSet<String>()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(part))).use { zos ->
                for (slice in OrderedSlices.ascending(slices)) {
                    val entryName = uniqueEntryName(slice.file.name, usedNames)
                    val entry = ZipEntry(entryName).apply {
                        time = slice.file.lastModified()
                    }
                    zos.putNextEntry(entry)
                    slice.file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    count++
                }
            }
            if (!part.renameTo(output)) {
                part.copyTo(output, overwrite = true)
                part.delete()
            }
            return count
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private fun uniqueEntryName(raw: String, used: MutableSet<String>): String {
        if (used.add(raw)) return raw
        val dot = raw.lastIndexOf('.')
        val base = if (dot > 0) raw.substring(0, dot) else raw
        val ext = if (dot > 0) raw.substring(dot) else ""
        var i = 2
        while (true) {
            val candidate = "${base}_$i$ext"
            if (used.add(candidate)) return candidate
            i++
        }
    }
}
