package com.changjietu.app.feature.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReprocessInFlightDirectoriesTest {
    @Test fun activeOutputIsProtectedUntilReleased() {
        val root = Files.createTempDirectory("inflight").toFile()
        try {
            val dir = File(root, "active").also { it.mkdirs() }
            ReprocessInFlightDirectories.acquire(dir)
            try {
                assertTrue(ReprocessInFlightDirectories.isProtected(dir))
                assertTrue(ReprocessInFlightDirectories.isProtected(File(dir, "partial.jpg")))
            } finally {
                ReprocessInFlightDirectories.release(dir)
            }
            assertFalse(ReprocessInFlightDirectories.isProtected(dir))
        } finally { root.deleteRecursively() }
    }

    @Test fun overlappingWritersDoNotUnprotectEachOther() {
        val root = Files.createTempDirectory("inflight").toFile()
        try {
            val dir = File(root, "active").also { it.mkdirs() }
            ReprocessInFlightDirectories.acquire(dir)
            ReprocessInFlightDirectories.acquire(dir)
            ReprocessInFlightDirectories.release(dir)
            assertTrue(ReprocessInFlightDirectories.isProtected(dir))
            ReprocessInFlightDirectories.release(dir)
            assertFalse(ReprocessInFlightDirectories.isProtected(dir))
        } finally { root.deleteRecursively() }
    }
}
