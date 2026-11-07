package com.changjietu.app.feature.export

import com.changjietu.app.image.processing.SliceOutput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class OrderedSlicesTest {
    private fun slice(index: Int, startY: Int): SliceOutput = SliceOutput(
        index = index,
        total = 3,
        startY = startY,
        endY = startY + 100,
        width = 1220,
        height = 100,
        file = File("longshot_%02d_of_03.jpg".format(index + 1)),
        label = "%02d/03".format(index + 1),
    )

    @Test fun shuffledSelectionIsSortedByOriginalVerticalPosition() {
        val slices = listOf(slice(2, 400), slice(0, 0), slice(1, 200))
        assertEquals(listOf(0, 200, 400), OrderedSlices.ascending(slices).map { it.startY })
    }

    @Test fun selectionOrderNeverChangesReadingOrder() {
        val original = listOf(slice(1, 200), slice(0, 0), slice(2, 400))
        assertEquals(listOf("01/03", "02/03", "03/03"),
            OrderedSlices.ascending(original).map { it.label })
    }
}
