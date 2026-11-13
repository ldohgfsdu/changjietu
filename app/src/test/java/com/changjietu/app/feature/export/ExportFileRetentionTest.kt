package com.changjietu.app.feature.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExportFileRetentionTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun twoSimultaneousReadersCannotBeCleanedMidExport() {
        val now = 2_000_000_000L
        val guard = ExportFileRetention(clock = { now })
        val dir = tmp.newFolder("old_slices")
        val file = File(dir, "01.jpg").apply { writeText("sample") }
        dir.setLastModified(now - 48L * 3600_000)
        val first = guard.acquire(listOf(file))
        val second = guard.acquire(listOf(file))
        assertFalse(guard.canPrune(dir, 9, 3))
        first.close()
        assertFalse(guard.canPrune(dir, 9, 3))
        second.close()
        assertTrue(guard.canPrune(dir, 9, 3))
    }

    @Test fun activeShareSurvivesProcessRecreationFor24Hours() {
        var now = 4_000_000_000L
        val dir = tmp.newFolder("old")
        val img = File(dir, "01.jpg").apply { writeText("sample") }
        dir.setLastModified(now - 48L * 3600_000)
        ExportFileRetention(clock = { now }).markShared(listOf(img))
        assertFalse(ExportFileRetention(clock = { now }).canPrune(dir, 5, 3))
        now += 25L * 3600_000
        assertTrue(ExportFileRetention(clock = { now }).canPrune(dir, 5, 3))
    }

    @Test fun zipSharingKeepsOnlyReferencedArchive() {
        val now = 5_000_000_000L
        val dir = tmp.newFolder("zip")
        val old = File(dir, "old.zip").apply { writeText("zip"); setLastModified(now - 48L * 3600_000) }
        val shared = File(dir, "shared.zip").apply { writeText("zip"); setLastModified(now - 48L * 3600_000) }
        val guard = ExportFileRetention(clock = { now })
        guard.markShared(listOf(shared))
        assertFalse(guard.canPrune(shared, 5, 3))
        assertTrue(guard.canPrune(old, 5, 3))
    }

    @Test fun currentGraceWindowAndIdempotentLeaseClose() {
        var now = 6_000_000_000L
        val dir = tmp.newFolder("fresh")
        dir.setLastModified(now)
        val guard = ExportFileRetention(clock = { now })
        val lease = guard.acquire(listOf(File(dir, "slice.jpg").apply { writeText("sample") }))
        // Writing the child updates the parent directory timestamp; restore the fake clock.
        dir.setLastModified(now)
        lease.close()
        lease.close()
        assertFalse(guard.canPrune(dir, 5, 3))
        now += 25L * 3600_000
        assertTrue(guard.canPrune(dir, 5, 3))
    }
    @Test fun pruneAndLeaseAcquisitionMustBeAtomic() {
        val now = 8_000_000_000L
        val guard = ExportFileRetention(clock = { now })
        val dir = tmp.newFolder("race")
        val file = File(dir, "01.jpg").apply { writeText("sample") }
        dir.setLastModified(now - 48L * 3600_000)
        val started = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val failure = arrayOfNulls<Throwable>(1)
        val reader = Thread {
            started.await()
            try { guard.acquire(listOf(file)).close() }
            catch (e: Throwable) { failure[0] = e }
            finally { finished.countDown() }
        }
        reader.start()
        try {
            assertTrue(guard.prune(dir, 9, 3) {
                started.countDown()
                // A new reader cannot acquire a lease between approval and deletion.
                assertFalse(finished.await(150, java.util.concurrent.TimeUnit.MILLISECONDS))
            })
            assertFalse(file.exists())
            assertTrue(finished.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(failure[0] is IllegalArgumentException)
        } finally {
            started.countDown()
            reader.join(3000)
        }
    }

    @Test fun sharedSliceDirectoryExpiresAtExact24HourBoundaryAfterProcessRecreation() {
        var now = 9_000_000_000L
        val grace = 24L * 3600_000
        val dir = tmp.newFolder("shared_boundary")
        val file = File(dir, "01.jpg").apply { writeText("sample") }
        dir.setLastModified(now - 48L * 3600_000)
        ExportFileRetention(clock = { now }).markShared(listOf(file))
        val recreated = ExportFileRetention(clock = { now })
        now += grace - 1
        assertFalse(recreated.canPrune(dir, 5, 3))
        assertTrue(file.exists())
        now += 1
        assertTrue(recreated.canPrune(dir, 5, 3))
    }

    @Test fun resharingArchiveRenewsGracePeriodAfterProcessRecreation() {
        var now = 10_000_000_000L
        val grace = 24L * 3600_000
        val dir = tmp.newFolder("zip_renewal")
        val archive = File(dir, "shared.zip").apply {
            writeText("zip")
            setLastModified(now - 48L * 3600_000)
        }
        val guard = ExportFileRetention(clock = { now })
        guard.markShared(listOf(archive))
        now += grace - 1
        guard.markShared(listOf(archive))
        val recreated = ExportFileRetention(clock = { now })
        now += 1
        assertFalse(recreated.canPrune(archive, 5, 3))
        now += grace - 1
        assertTrue(recreated.canPrune(archive, 5, 3))
    }
}
