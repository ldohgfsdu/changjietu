package com.changjietu.app.feature.editor

/** Reconcile the live UI selection only when asynchronous reprocessing finishes. */
internal object ReprocessSelectionPolicy {
    fun resolve(
        currentSelection: Set<Int>,
        currentSliceCount: Int,
        newSliceCount: Int,
    ): Set<Int> = if (currentSliceCount == newSliceCount) {
        currentSelection.filterTo(mutableSetOf()) { it in 0 until newSliceCount }
    } else {
        (0 until newSliceCount).toSet()
    }
}
