package com.changjietu.app.feature.export

import com.changjietu.app.image.processing.SliceOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GallerySaveBatchTest {
    private fun slice(index: Int, startY: Int): SliceOutput = SliceOutput(
        index = index,
        total = 3,
        startY = startY,
        endY = startY + 99,
        width = 720,
        height = 100,
        file = File("slice_${index}.jpg"),
        label = "${index + 1}/3",
    )

    @Test fun partialFailureReturnsOnlyFailedItemsInReadingOrder() {
        val input = listOf(slice(2, 200), slice(0, 0), slice(1, 100))
        val written = mutableListOf<Int>()
        val first = saveSliceBatch(input) {
            written += it.index
            it.index != 1
        }
        assertEquals(listOf(0, 1, 2), written)
        assertEquals(2, first.saved)
        assertEquals(1, first.failed)
        assertEquals(listOf(1), first.failedSlices.map { it.index })
        assertEquals(3, first.total)

        val retried = mutableListOf<Int>()
        val retry = saveSliceBatch(first.failedSlices) {
            retried += it.index
            true
        }
        assertEquals(listOf(1), retried)
        assertEquals(1, retry.saved)
        assertEquals(0, retry.failed)
        assertTrue(retry.failedSlices.isEmpty())
    }

    @Test fun failedWriteDoesNotPreventFollowingSlicesFromSaving() {
        val calls = mutableListOf<Int>()
        val result = saveSliceBatch(listOf(slice(0, 0), slice(1, 100), slice(2, 200))) {
            calls += it.index
            if (it.index == 1) throw IllegalStateException("no space")
            true
        }
        assertEquals(listOf(0, 1, 2), calls)
        assertEquals(2, result.saved)
        assertEquals(listOf(1), result.failedSlices.map { it.index })
    }

    @Test(expected = java.util.concurrent.CancellationException::class)
    fun cancellationIsNotMistakenForRecoverableWriteFailure() {
        saveSliceBatch(listOf(slice(0, 0))) {
            throw java.util.concurrent.CancellationException("cancelled")
        }
    }
}
