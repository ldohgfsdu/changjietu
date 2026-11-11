package com.changjietu.app.feature.editor

import java.io.File

/**
 * Tracks output directories while background encoders can still write into them.
 * Cleanup must not prune a directory that is being populated by another task.
 */
internal object ReprocessInFlightDirectories {
    private val writers = mutableMapOf<File, Int>()

    @Synchronized
    fun create(root: File): File = ReprocessOutputDirectory.create(root).also(::acquire)

    @Synchronized
    fun acquire(directory: File) {
        val key = directory.absoluteFile
        writers[key] = (writers[key] ?: 0) + 1
    }

    @Synchronized
    fun release(directory: File) {
        val key = directory.absoluteFile
        val count = writers[key] ?: return
        if (count <= 1) writers.remove(key) else writers[key] = count - 1
    }

    @Synchronized
    fun isProtected(path: File): Boolean {
        val candidate = path.absoluteFile.toPath().normalize()
        return writers.keys.any { candidate.startsWith(it.toPath().normalize()) }
    }
}
