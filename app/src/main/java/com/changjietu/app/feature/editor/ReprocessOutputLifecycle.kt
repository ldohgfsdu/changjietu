package com.changjietu.app.feature.editor

import java.io.File

/** Only the result adopted by the editor may keep its output files. */
internal object ReprocessOutputLifecycle {
    fun cleanupIfUncommitted(directory: File?, committed: Boolean) {
        if (!committed) directory?.deleteRecursively()
    }
}
