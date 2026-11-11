package com.changjietu.app.feature.editor

import java.io.File
import java.nio.file.Files

/**
 * Atomic unique directory allocation for overlapping or rapidly cancelled reprocess jobs.
 * A millisecond timestamp is not a unique job identifier.
 */
internal object ReprocessOutputDirectory {
    fun create(root: File): File {
        if (!root.isDirectory && !root.mkdirs()) {
            check(root.isDirectory) { "无法创建切片缓存目录" }
        }
        return Files.createTempDirectory(root.toPath(), "reprocess-").toFile()
    }
}
