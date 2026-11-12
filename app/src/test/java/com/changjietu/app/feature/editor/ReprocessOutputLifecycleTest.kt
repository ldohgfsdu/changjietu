package com.changjietu.app.feature.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotSame
import org.junit.Assert.fail
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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

    @Test fun cancelledCleanupRunsOffCallerAndReleasesWriterAfterDeletion() = runBlocking {
        val root = Files.createTempDirectory("reprocess-cancel-io").toFile()
        val dir = ReprocessInFlightDirectories.create(root)
        File(dir, "partial.jpg").writeText("partial")
        val cleanupStarted = CountDownLatch(1)
        val allowCleanup = CountDownLatch(1)
        val cleanupThread = AtomicReference<Thread?>()
        val callerThread = Thread.currentThread()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            ReprocessOutputLifecycle.finish(dir, committed = false) { target ->
                cleanupThread.set(Thread.currentThread())
                cleanupStarted.countDown()
                check(allowCleanup.await(5, TimeUnit.SECONDS)) { "cleanup barrier timed out" }
                target.deleteRecursively()
            }
        }
        try {
            assertTrue("cleanup did not start", cleanupStarted.await(5, TimeUnit.SECONDS))
            assertNotSame("recursive cleanup ran on the caller (UI) thread", callerThread, cleanupThread.get())
            assertTrue("writer protection lost before cleanup", ReprocessInFlightDirectories.isProtected(dir))
            job.cancel()
        } finally {
            allowCleanup.countDown()
            job.join()
            ReprocessInFlightDirectories.release(dir)
            root.deleteRecursively()
        }
        assertFalse("cancelled cleanup left files behind", dir.exists())
        assertFalse("writer protection was leaked", ReprocessInFlightDirectories.isProtected(dir))
    }

    @Test fun adoptedOutputSurvivesCleanupAndRegistrationIsReleased() = runBlocking {
        val root = Files.createTempDirectory("reprocess-committed-io").toFile()
        try {
            val dir = ReprocessInFlightDirectories.create(root)
            val file = File(dir, "slice.jpg").also { it.writeText("keep") }
            ReprocessOutputLifecycle.finish(dir, committed = true)
            assertTrue(file.exists())
            assertFalse(ReprocessInFlightDirectories.isProtected(dir))
        } finally { root.deleteRecursively() }
    }

    @Test fun failedCleanupStillReleasesWriterProtection() {
        val root = Files.createTempDirectory("reprocess-cleanup-failure").toFile()
        try {
            val dir = ReprocessInFlightDirectories.create(root)
            try {
                runBlocking {
                    ReprocessOutputLifecycle.finish(dir, committed = false) {
                        throw IOException("simulated storage failure")
                    }
                }
                fail("expected simulated storage error")
            } catch (_: IOException) {
                assertFalse(ReprocessInFlightDirectories.isProtected(dir))
            }
        } finally { root.deleteRecursively() }
    }
}
