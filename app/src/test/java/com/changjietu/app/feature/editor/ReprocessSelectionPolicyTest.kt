package com.changjietu.app.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ReprocessSelectionPolicyTest {
    @Test fun keepsLatestSelectionWhenSliceCountUnchanged() {
        assertEquals(setOf(1), ReprocessSelectionPolicy.resolve(
            currentSelection = setOf(1),
            currentSliceCount = 3,
            newSliceCount = 3,
        ))
    }

    @Test fun selectsAllWhenSliceCountChanges() {
        assertEquals(setOf(0, 1, 2, 3), ReprocessSelectionPolicy.resolve(
            currentSelection = setOf(1),
            currentSliceCount = 3,
            newSliceCount = 4,
        ))
    }

    @Test fun removesInvalidIndices() {
        assertEquals(setOf(0, 2), ReprocessSelectionPolicy.resolve(
            currentSelection = setOf(-1, 0, 2, 9),
            currentSliceCount = 3,
            newSliceCount = 3,
        ))
    }
}
