package com.changjietu.app.feature.editor

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reprocess runs in viewModelScope (Main). Recursive disk deletion must not happen
 * directly in its finally block, especially when rapid edits cancel a large encode.
 */
class ReprocessCleanupSchedulingContractTest {
    @Test fun cancelledReprocessDoesNotDeleteFilesInlineOnMain() {
        val relative = "src/main/java/com/changjietu/app/feature/editor/LongshotViewModel.kt"
        val source = listOf(File(relative), File("app", relative)).first { it.isFile }.readText()
        assertFalse(
            "Synchronous recursive cleanup in a Main coroutine can freeze cut editing",
            source.contains("ReprocessOutputLifecycle.cleanupIfUncommitted(outputDirectory, committed)"),
        )
        assertTrue(
            "Abandoned output must still be cleaned by the cancellation-safe IO path",
            source.contains("ReprocessOutputLifecycle.finish(outputDirectory, committed)"),
        )
    }
}
