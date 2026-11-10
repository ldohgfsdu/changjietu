package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRecoveryTest {
    @Test fun processResumedWithFreshViewModel_goesHomeInsteadOfDeadEnd() {
        assertEquals(RecoveryDestination.HOME, NavigationRecovery.forProcess(false, false))
    }

    @Test fun previewResumedWithFreshViewModel_goesHomeInsteadOfTwoDeadEnds() {
        assertEquals(RecoveryDestination.HOME, NavigationRecovery.forPreview(false, false, false))
    }

    @Test fun validImportInProgress_doesNotPrematurelyNavigateHome() {
        assertEquals(RecoveryDestination.STAY, NavigationRecovery.forProcess(false, true))
        assertEquals(RecoveryDestination.STAY, NavigationRecovery.forPreview(false, false, true))
    }

    @Test fun decodedSourceRemainsOnProcessScreen() {
        assertEquals(RecoveryDestination.STAY, NavigationRecovery.forProcess(true, false))
    }

    @Test fun previewMissingSlicesButSourceValid_recoversToProcess() {
        assertEquals(RecoveryDestination.PROCESS, NavigationRecovery.forPreview(false, true, false))
    }

    @Test fun validRenderedSlicesStayVisible() {
        assertEquals(RecoveryDestination.STAY, NavigationRecovery.forPreview(true, true, false))
        assertEquals(RecoveryDestination.STAY, NavigationRecovery.forPreview(true, false, false))
    }
}
