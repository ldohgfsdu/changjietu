package com.changjietu.app.core

/**
 * Recover a restored navigation destination when its in-memory ViewModel data
 * was discarded (for example after Android kills and recreates the process).
 *
 * This does not restore decoded source data or lost edits. It only prevents the
 * user from being stranded on an invalid screen while leaving an active import
 * undisturbed.
 */
enum class RecoveryDestination {
    STAY,
    HOME,
    PROCESS,
}

object NavigationRecovery {
    fun forProcess(hasSource: Boolean, isOpeningSource: Boolean): RecoveryDestination = when {
        hasSource || isOpeningSource -> RecoveryDestination.STAY
        else -> RecoveryDestination.HOME
    }

    fun forPreview(
        hasRenderedSlices: Boolean,
        hasSource: Boolean,
        isOpeningSource: Boolean,
    ): RecoveryDestination = when {
        hasRenderedSlices || isOpeningSource -> RecoveryDestination.STAY
        hasSource -> RecoveryDestination.PROCESS
        else -> RecoveryDestination.HOME
    }
}
