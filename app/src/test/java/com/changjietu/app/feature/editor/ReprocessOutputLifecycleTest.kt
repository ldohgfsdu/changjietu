package com.changjietu.app.feature.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReprocessOutputLifecycleTest {
    @Test fun abandonedOutputIsDeletedIncludingPartialFiles() {
        val root = Files.createTempDirectory("reprocess-lifecycle").toFile()
        try {
            val dir = ReprocessOutputDirectory.create(root)
            File(dir, "partial.jpg").writeText("partial")
            ReprocessOutputLifecycle.cleanupIfUncommitted(dir, committed = false)
            assertFalse(dir.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun committedOutputRemainsAvailable() {
        val root = Files.createTempDirectory("reprocess-lifecycle").toFile()
        try {
            val dir = ReprocessOutputDirectory.create(root)
            File(dir, "slice.jpg").writeText("slice")
            ReprocessOutputLifecycle.cleanupIfUncommitted(dir, committed = true)
            assertTrue(File(dir, "slice.jpg").exists())
        } finally { root.deleteRecursively() }
    }
}
