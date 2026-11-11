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
    @Test fun competingCleanupCannotObserveActiveOutputAsUnprotected() {
        val root = Files.createTempDirectory("inflight-race").toFile()
        val registered = java.util.concurrent.CountDownLatch(1)
        val allowRelease = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val output = File(root, "active")
        val writer = Thread {
            try {
                ReprocessInFlightDirectories.acquire(output)
                registered.countDown()
                check(allowRelease.await(5, java.util.concurrent.TimeUnit.SECONDS))
            } finally {
                ReprocessInFlightDirectories.release(output)
                finished.countDown()
            }
        }
        try {
            writer.start()
            assertTrue("writer did not register", registered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            // Simulates a newer reprocess scanning the cache while the old encoder is blocked.
            assertTrue(ReprocessInFlightDirectories.isProtected(output))
            assertTrue(ReprocessInFlightDirectories.isProtected(File(output, "partial.jpg")))
        } finally {
            allowRelease.countDown()
            assertTrue("writer did not finish", finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
            writer.join(5000)
            assertFalse(ReprocessInFlightDirectories.isProtected(output))
            root.deleteRecursively()
        }
    }

}
