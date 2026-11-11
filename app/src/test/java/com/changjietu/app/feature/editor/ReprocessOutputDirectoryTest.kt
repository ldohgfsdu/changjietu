package com.changjietu.app.feature.editor

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReprocessOutputDirectoryTest {
    @Test fun concurrentRequestsNeverReuseTheSameOutputDirectory() {
        val root = Files.createTempDirectory("reprocess-output-test").toFile()
        try {
            val a = ReprocessOutputDirectory.create(root)
            val b = ReprocessOutputDirectory.create(root)
            assertNotEquals(a.canonicalPath, b.canonicalPath)
            assertTrue(a.isDirectory)
            assertTrue(b.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
