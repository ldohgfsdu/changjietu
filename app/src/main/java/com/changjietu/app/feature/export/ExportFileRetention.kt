package com.changjietu.app.feature.export

import java.io.File
import kotlin.math.max

/** Keep active export readers alive and give shared cache files a 24h grace period. */
internal class ExportFileRetention(
    private val clock: () -> Long = System::currentTimeMillis,
    private val graceMs: Long = 24L * 60 * 60 * 1000,
) {
    private val readers = mutableMapOf<String, Int>()
    private val sharedUntil = mutableMapOf<String, Long>()
    private fun key(file: File) = file.absolutePath

    fun covers(entry: File, file: File): Boolean {
        val root = key(entry).trimEnd(File.separatorChar)
        val path = key(file)
        return path == root || path.startsWith(root + File.separator)
    }

    @Synchronized fun acquire(files: Collection<File>): AutoCloseable {
        val keys = files.map(::key).distinct()
        keys.forEach { readers[it] = (readers[it] ?: 0) + 1 }
        var closed = false
        return AutoCloseable {
            synchronized(this) {
                if (!closed) {
                    closed = true
                    keys.forEach {
                        val count = (readers[it] ?: 0) - 1
                        if (count <= 0) readers.remove(it) else readers[it] = count
                    }
                }
            }
        }
    }

    @Synchronized fun markShared(files: Collection<File>) {
        val now = clock()
        for (file in files) {
            val path = key(file)
            sharedUntil[path] = max(sharedUntil[path] ?: 0L, now + graceMs)
            // Persist protection across process recreation, too.
            if (file.exists()) file.setLastModified(now)
            file.parentFile?.takeIf(File::exists)?.setLastModified(now)
        }
    }

    @Synchronized fun canPrune(entry: File, rank: Int, keep: Int): Boolean {
        if (rank < keep || !entry.exists()) return false
        val now = clock()
        if (now - entry.lastModified() < graceMs) return false
        if (readers.keys.any { covers(entry, File(it)) }) return false
        val iterator = sharedUntil.iterator()
        while (iterator.hasNext()) {
            val (file, end) = iterator.next()
            if (end <= now) iterator.remove()
            else if (covers(entry, File(file))) return false
        }
        return true
    }
}
