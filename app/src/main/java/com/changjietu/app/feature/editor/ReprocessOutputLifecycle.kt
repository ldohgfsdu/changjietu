package com.changjietu.app.feature.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/** Only the result adopted by the editor may keep its output files. */
internal object ReprocessOutputLifecycle {
    fun cleanupIfUncommitted(directory: File?, committed: Boolean) {
        if (!committed) directory?.deleteRecursively()
    }

    /**
     * viewModelScope resumes on Main after the encoder's IO context.
     * Recursive removal of partial slices may be slow, so always dispatch
     * cleanup off Main. NonCancellable guarantees that cancelled reprocesses
     * still delete uncommitted output and release their writer registration.
     */
    suspend fun finish(
        directory: File?,
        committed: Boolean,
        cleanup: (File) -> Unit = { cleanupIfUncommitted(it, committed = false) },
    ) {
        if (directory == null) return
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                if (!committed) cleanup(directory)
            } finally {
                ReprocessInFlightDirectories.release(directory)
            }
        }
    }
}
