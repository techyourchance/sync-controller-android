package com.techyourchance.synccontroller

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.CopyOnWriteArraySet

class SyncControllerTest {

    // region constants ----------------------------------------------------------------------------
    // endregion constants -------------------------------------------------------------------------

    // region helper fields ------------------------------------------------------------------------
    private val syncPreferencesDelegateTd = SyncPreferencesDelegateTd()
    private val realtimeSyncDelegate = RealtimeSyncDelegateTd()
    private val httpSyncDelegate = HttpSyncDelegateTd()
    private val longSyncDelegate = LongSyncDelegateTd()
    private val loginStateDelegate = LoginStateDelegateTd()
    private val appForegroundStateDelegate = AppForegroundStateDelegateTd()
    private val loggerDelegate = object : LoggerDelegate {
        override fun d(tag: String, message: String) {
            // no-op
        }
    }
    // endregion helper fields ---------------------------------------------------------------------

    @Suppress("PrivatePropertyName")
    private lateinit var SUT: SyncController

    @Before
    fun setup() {
        SUT = SyncController(
                loginStateDelegate,
                syncPreferencesDelegateTd,
                realtimeSyncDelegate,
                httpSyncDelegate,
                longSyncDelegate,
                appForegroundStateDelegate,
                loggerDelegate,
        )
    }


    @Test
    fun syncStateIdle_noListenersRegistered() {
        // Arrange
        // Act
        // Assert
        assertNoListenersRegistered()
    }

    @Test
    fun getSyncInfo_initially_syncStateIdle() {
        // Arrange
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.IDLE))
    }

    @Test
    fun startSync_firstEverSync_fullLongSyncStarted() {
        // Arrange
        arrangeFirstEverSyncRequired()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleFullLongSyncStarted()
    }

    @Test
    fun getSyncInfo_firstEverSync_syncStateFirstEverSync() {
        // Arrange
        arrangeFirstEverSyncRequired()
        SUT.startSync()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.FIRST_EVER_SYNC))
    }

    @Test
    fun startSync_firstSync_httpSyncStarted() {
        // Arrange
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun getSyncInfo_firstSync_syncStateFirstSync() {
        // Arrange
        SUT.startSync()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.FIRST_SYNC))
    }

    @Test
    fun getSyncInfo_afterFirstEverSyncCompleted_syncStateRealtime() {
        // Arrange
        executeFirstEverSyncFlow()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.REALTIME))
    }

    @Test
    fun startSync_afterFirstEverSyncCompleted_realtimeSyncStarted() {
        // Arrange
        executeFirstEverSyncFlow()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleRealtimeSyncStarted()
    }

    @Test
    fun getSyncInfo_afterFirstEverSyncCompletedRealtimeInitFailed_syncStateFallback() {
        // Arrange
        executeFirstEverSyncFlowAndFailRealtimeInit()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.FALLBACK))
    }

    @Test
    fun getSyncInfo_afterFirstSyncCompleted_syncStateRealtime() {
        // Arrange
        executeFirstSyncFlow()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.REALTIME))
    }

    @Test
    fun startSync_afterFirstSyncCompleted_realtimeSyncStarted() {
        // Arrange
        executeFirstSyncFlow()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleRealtimeSyncStarted()
    }

    @Test
    fun getSyncInfo_afterFirstSyncCompletedRealtimeInitFailed_syncStateFallback() {
        // Arrange
        executeFirstSyncFlowAndFailRealtimeInit()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.FALLBACK))
    }

    @Test
    fun startSync_afterFirstSyncCompletedRealtimeInitFailed_httpSyncStarted() {
        // Arrange
        executeFirstSyncFlowAndFailRealtimeInit()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun startSync_afterFirstSyncCompletedRealtimeInitFailed_realtimeReInitialized() {
        // Arrange
        executeFirstSyncFlowAndFailRealtimeInit()
        arrangeHttpSyncCompletesNormally()
        // Act
        SUT.startSync()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.REALTIME))
    }

    @Test
    fun startSync_firstSyncInProgress_noParallelSyncs() {
        // Arrange
        // Act
        SUT.startSync()
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun startSync_firstEverSyncInProgress_noParallelSyncs() {
        // Arrange
        arrangeFirstEverSyncRequired()
        // Act
        SUT.startSync()
        SUT.startSync()
        // Assert
        assertOnlySingleFullLongSyncStarted()
    }

    @Test
    fun startSync_fallbackSyncInProgress_noParallelSyncs() {
        // Arrange
        arrangeFallbackState()
        // Act
        SUT.startSync()
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun startSync_fallbackSyncCompletedAndRealtimeInitFailed_httpSyncStarted() {
        // Arrange
        arrangeHttpSyncCompletesNormally()
        realtimeSyncDelegate.shouldInitFail = true
        // bring to fallback state from IDLE
        SUT.startSync()
        // execute full fallback sync
        SUT.startSync()
        // reset state aggregated during arrange
        httpSyncDelegate.resetTestState()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun startSync_realtimeSyncInProgress_parallelSyncStarted() {
        // Arrange
        arrangeRealtimeState()
        // Act
        SUT.startSync()
        SUT.startSync()
        // Assert
        assertRealtimeSyncsStarted(2)
    }

    @Test
    fun startSync_firstEverSyncNoLoggedInUser_ignored() {
        // Arrange
        arrangeFirstEverSyncRequired()
        arrangeNoLoggedInUser()
        // Act
        SUT.startSync()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.IDLE))
        assertNoSyncStarted()
    }

    @Test
    fun startSync_firstSyncNoLoggedInUser_ignored() {
        // Arrange
        arrangeNoLoggedInUser()
        // Act
        SUT.startSync()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.IDLE))
        assertNoSyncStarted()
    }

    @Test
    fun getSyncInfo_firstEverSyncWhenAppInBackground_syncStateFallback() {
        // Arrange
        arrangeAppInBackground()
        executeFirstEverSyncFlow()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.FALLBACK))
    }

    @Test
    fun getSyncInfo_firstSyncWhenAppInBackground_syncStateFallback() {
        // Arrange
        arrangeAppInBackground()
        executeFirstSyncFlow()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.FALLBACK))
    }

    @Test
    fun getSyncInfo_afterFallbackSyncAppInBackground_syncStateFallback() {
        // Arrange
        arrangeFallbackState()
        arrangeAppInBackground()
        arrangeHttpSyncCompletesNormally()
        SUT.startSync()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.FALLBACK))
    }

    @Test
    fun getSyncInfo_syncStateIdleAppGoesToBackground_syncStateIdle() {
        // Arrange
        appForegroundStateDelegate.notifyAppTransitionedToBackground()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.IDLE))
    }

    @Test
    fun getSyncInfo_syncStateRealtimeAppGoesToBackground_syncStateFallback() {
        // Arrange
        arrangeRealtimeState()
        appForegroundStateDelegate.notifyAppTransitionedToBackground()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.FALLBACK))
    }

    @Test
    fun syncStateRealtimeAppGoesToBackground_realtimeSyncShutDown() {
        // Arrange
        arrangeRealtimeState()
        appForegroundStateDelegate.notifyAppTransitionedToBackground()
        // Act
        // Assert
        assertFalse(realtimeSyncDelegate.isOperational)
    }

    @Test
    fun getSyncInfo_syncStateIdleAppGoesToForeground_syncStateIdle() {
        // Arrange
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.IDLE))
    }

    @Test
    fun getSyncInfo_syncStateIdleAppGoesToForeground_realtimeSyncNotInitialized() {
        // Arrange
        realtimeSyncDelegate.isOperational = false
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        // Assert
        assertThat(realtimeSyncDelegate.isOperational, `is`(false))
    }

    @Test
    fun getSyncInfo_syncStateIdleAppGoesToForeground_noSyncStarted() {
        // Arrange
        realtimeSyncDelegate.isOperational = false
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        // Assert
        assertNoSyncStarted()
    }

    @Test
    fun getSyncInfo_syncStateFirstEverSyncAppGoesToForeground_syncStateFirstEverSync() {
        // Arrange
        arrangeFirstEverSyncRequired()
        realtimeSyncDelegate.isOperational = false
        SUT.startSync()
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        val result = SUT.getSyncInfo()
        // Assert
        assertThat(result.syncState, `is`(SyncState.FIRST_EVER_SYNC))
    }

    @Test
    fun getSyncInfo_syncStateFirstEverSyncAppGoesToForeground_realtimeSyncNotInitialized() {
        // Arrange
        arrangeFirstEverSyncRequired()
        realtimeSyncDelegate.isOperational = false
        SUT.startSync()
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        // Assert
        assertThat(realtimeSyncDelegate.isOperational, `is`(false))
    }

    @Test
    fun getSyncInfo_syncStateFirstEverSyncAppGoesToForeground_noSyncStarted() {
        // Arrange
        arrangeFirstEverSyncRequired()
        realtimeSyncDelegate.isOperational = false
        SUT.startSync()
        longSyncDelegate.resetTestState()
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        // Assert
        assertNoSyncStarted()
    }

    @Test
    fun syncStateFallbackAppGoesToForeground_httpSyncStarted() {
        // Arrange
        arrangeFallbackState()
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun getSyncInfo_syncStateFallbackAppGoesToForegroundHttpSyncCompletesNormally_syncStateRealtime() {
        // Arrange
        arrangeFallbackState()
        arrangeHttpSyncCompletesNormally()
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.REALTIME))
    }

    @Test
    fun getSyncInfo_syncStateFallbackAppGoesToForegroundRealtimeInitFails_syncStateFallback() {
        // Arrange
        arrangeFallbackState()
        realtimeSyncDelegate.shouldInitFail = true
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.FALLBACK))
    }

    @Test
    fun getSyncInfo_syncStateFallbackSyncInProgressAppGoesToForeground_realtimeSyncNotInitialized() {
        // Arrange
        arrangeFallbackState()
        SUT.startSync()
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        // Assert
        assertThat(realtimeSyncDelegate.isOperational, `is`(false))
    }

    @Test
    fun getSyncInfo_syncStateFallbackSyncInProgressAppGoesToForeground_syncStateFallback() {
        // Arrange
        arrangeFallbackState()
        SUT.startSync()
        appForegroundStateDelegate.notifyAppTransitionedToForeground()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.FALLBACK))
    }

    @Test
    fun startSync_syncStateFallbackHttpSyncCompletesUnsuccessfully_syncStateRecovery() {
        // Arrange
        arrangeFallbackState()
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY
        // Act
        SUT.startSync()
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.RECOVERY_SYNC))
    }

    @Test
    fun startSync_syncStateFallbackHttpSyncCompletesUnsuccessfully_fullLongSyncStarted() {
        // Arrange
        arrangeFallbackState()
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY
        // Act
        SUT.startSync()
        // Assert
        assertHttpSyncStartedAndThenFullLongSyncStarted()
    }

    @Test
    fun getSyncInfo_syncStateFallbackRecoveryStartsAndEnds_realtimeSyncReInitialized() {
        // Arrange
        arrangeFallbackState()
        arrangeRecoveryStartsAndEndsDuringHttpSync()
        // Act
        SUT.startSync()
        // Assert
        assertTrue(realtimeSyncDelegate.isOperational)
    }

    @Test
    fun getSyncInfo_syncStateFallbackRecoveryStartsAndEnds_syncStateRealtime() {
        // Arrange
        arrangeFallbackState()
        arrangeRecoveryStartsAndEndsDuringHttpSync()
        // Act
        SUT.startSync()
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.REALTIME))
    }

    @Test
    fun startSync_recoveryCompletedRealtimeInitFailed_httpSyncStarted() {
        // Arrange
        arrangeFallbackState()
        arrangeRecoveryStartsAndEndsDuringHttpSync()
        realtimeSyncDelegate.shouldInitFail = true
        SUT.startSync()
        httpSyncDelegate.resetTestState()
        longSyncDelegate.resetTestState()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun startSync_syncStateFallbackHttpSyncTimedOut_longSyncStarted() {
        // Arrange
        arrangeFallbackState()
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        // Act
        SUT.startSync()
        // Assert
        assertHttpSyncStartedAndThenLongSyncStarted()
    }

    @Test
    fun getSyncInfo_syncStateFallbackHttpSyncTimedOut_syncStatePostTimeoutSync() {
        // Arrange
        arrangeFallbackState()
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        // Act
        SUT.startSync()
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.POST_TIMEOUT_SYNC))
    }

    @Test
    fun startSync_afterLongSyncFollowingHttpSyncTimedOut_realtimeSyncReInitialized() {
        // Arrange
        arrangeFallbackState()
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
        // Act
        assertFalse(realtimeSyncDelegate.isOperational)
        SUT.startSync()
        // Assert
        assertTrue(realtimeSyncDelegate.isOperational)
    }

    @Test
    fun startSync_longSyncInProgressAfterHttpSyncTimedOut_ignored() {
        // Arrange
        arrangeFallbackState()
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        SUT.startSync()
        httpSyncDelegate.resetTestState()
        longSyncDelegate.resetTestState()
        // Act
        SUT.startSync()
        // Assert
        assertNoSyncStarted()
    }

    @Test
    fun startSync_afterLongSyncFollowingHttpSyncTimedOutRealtimeInitFailed_httpSyncStarted() {
        // Arrange
        arrangeFallbackState()
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
        realtimeSyncDelegate.shouldInitFail = true
        SUT.startSync()
        httpSyncDelegate.resetTestState()
        longSyncDelegate.resetTestState()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun startSync_syncStateRealtimeRealtimeSyncTimedOut_longSyncStarted() {
        // Arrange
        arrangeRealtimeState()
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        // Act
        SUT.startSync()
        // Assert
        assertRealtimeSyncStartedAndThenLongSyncStarted()
    }

    @Test
    fun getSyncInfo_syncStateRealtimeRealtimeSyncTimedOut_syncStatePostTimeoutSync() {
        // Arrange
        arrangeRealtimeState()
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        // Act
        SUT.startSync()
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.POST_TIMEOUT_SYNC))
    }

    @Test
    fun syncStateRealtimeRealtimeSyncTimedOut_realtimeSyncShutDown() {
        // Arrange
        arrangeRealtimeState()
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        // Act
        assertTrue(realtimeSyncDelegate.isOperational)
        SUT.startSync()
        // Assert
        assertFalse(realtimeSyncDelegate.isOperational)
    }

    @Test
    fun startSync_afterLongSyncFollowingRealtimeSyncTimedOut_realtimeSyncReInitialized() {
        // Arrange
        arrangeRealtimeState()
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
        // Act
        SUT.startSync()
        // Assert
        assertTrue(realtimeSyncDelegate.isOperational)
    }

    @Test
    fun startSync_afterLongSyncFollowingRealtimeSyncTimedOut_realtimeSyncStarted() {
        // Arrange
        arrangeRealtimeState()
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
        SUT.startSync()
        realtimeSyncDelegate.resetTestState()
        longSyncDelegate.resetTestState()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleRealtimeSyncStarted()
    }

    @Test
    fun startSync_afterLongSyncFollowingRealtimeSyncTimedOutRealtimeInitFailed_httpSyncStarted() {
        // Arrange
        arrangeRealtimeState()
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_TIMEOUT
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
        realtimeSyncDelegate.shouldInitFail = true
        SUT.startSync()
        realtimeSyncDelegate.resetTestState()
        longSyncDelegate.resetTestState()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    @Test
    fun startSync_syncStateRealtimeSyncCompletesUnsuccessfully_realtimeSyncShutDown() {
        // Arrange
        arrangeRealtimeState()
        arrangeRecoveryStartsDuringRealtimeSync()
        // Act
        SUT.startSync()
        // Assert
        assertThat(realtimeSyncDelegate.isOperational, `is`(false))
    }

    @Test
    fun startSync_syncStateRealtimeSyncCompletesUnsuccessfully_fullLongSyncStarted() {
        // Arrange
        arrangeRealtimeState()
        arrangeRecoveryStartsDuringRealtimeSync()
        // Act
        SUT.startSync()
        // Assert
        assertRealtimeSyncStartedAndThenFullLongSyncStarted()
    }

    @Test
    fun getSyncInfo_syncStateRealtimeSyncCompletesUnsuccessfully_syncStateRecovery() {
        // Arrange
        arrangeRealtimeState()
        arrangeRecoveryStartsDuringRealtimeSync()
        // Act
        SUT.startSync()
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.RECOVERY_SYNC))
    }

    @Test
    fun getSyncInfo_syncStateRealtimeRecoveryCompleted_realtimeSyncReInitialized() {
        // Arrange
        arrangeRealtimeState()
        arrangeRecoveryStartsAndEndsDuringRealtimeSync()
        SUT.startSync()
        // Act
        val result = realtimeSyncDelegate.isRealTimeSyncOperational()
        // Assert
        assertThat(result, `is`(true))
    }

    @Test
    fun getSyncInfo_syncStateRealtimeRecoveryCompleted_syncStateRealtime() {
        // Arrange
        arrangeRealtimeState()
        arrangeRecoveryStartsAndEndsDuringRealtimeSync()
        SUT.startSync()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.REALTIME))
    }

    @Test
    fun startSync_syncStateRecoverySync_ignored() {
        // Arrange
        arrangeRecoveryStateDuringFallback()
        // Act
        SUT.startSync()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.RECOVERY_SYNC))
        assertNoSyncStarted()
    }

    @Test
    fun getSyncInfo_syncStateRealtimeRealtimeSyncShutDown_syncStateFallback() {
        // Arrange
        arrangeRealtimeState()
        realtimeSyncDelegate.spuriousShutDown()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.FALLBACK))
    }

    @Test
    fun getSyncInfo_syncStateIdleRealtimeSyncShutDown_syncStateIdle() {
        // Arrange
        realtimeSyncDelegate.spuriousShutDown()
        // Act
        val result = SUT.getSyncInfo().syncState
        // Assert
        assertThat(result, `is`(SyncState.IDLE))
    }


    @Test
    fun reset_syncStateFallback_syncStateIdle() {
        // Arrange
        arrangeFallbackState()
        // Act
        SUT.reset()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.IDLE))
    }

    @Test
    fun reset_syncStateFallback_noListenersRegistered() {
        // Arrange
        arrangeFallbackState()
        // Act
        SUT.reset()
        // Assert
        assertNoListenersRegistered()
    }

    @Test
    fun reset_syncStateRealtime_realtimeSyncShutDown() {
        // Arrange
        arrangeRealtimeState()
        // Act
        SUT.reset()
        // Assert
        assertThat(realtimeSyncDelegate.isRealTimeSyncOperational(), `is`(false))
    }

    @Test
    fun reset_syncStateRealtime_syncStateIdle() {
        // Arrange
        arrangeRealtimeState()
        // Act
        SUT.reset()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.IDLE))
    }

    @Test
    fun reset_syncStateRealtime_noListenersRegistered() {
        // Arrange
        arrangeRealtimeState()
        // Act
        SUT.reset()
        // Assert
        assertNoListenersRegistered()
    }

    @Test
    fun reset_syncStateRecovery_syncStateIdle() {
        // Arrange
        arrangeRecoveryStateDuringFallback()
        // Act
        SUT.reset()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.IDLE))
    }

    @Test
    fun reset_syncStateRecovery_noListenersRegistered() {
        // Arrange
        arrangeRecoveryStateDuringFallback()
        // Act
        SUT.reset()
        // Assert
        assertNoListenersRegistered()
    }

    @Test
    fun startSync_afterReset_syncStateFirstSync() {
        // Arrange
        arrangeFallbackState()
        SUT.reset()
        // Act
        SUT.startSync()
        // Assert
        assertThat(SUT.getSyncInfo().syncState, `is`(SyncState.FIRST_SYNC))
    }

    @Test
    fun startSync_afterResetDuringFallbackSync_httpSyncStarted() {
        // Arrange
        arrangeFallbackState()
        SUT.startSync() // fallback sync started
        SUT.reset()
        arrangeFallbackState()
        // Act
        SUT.startSync()
        // Assert
        assertOnlySingleHttpSyncStarted()
    }

    // region helper methods -----------------------------------------------------------------------

    private fun executeFirstEverSyncFlow() {
        arrangeFirstEverSyncRequired()
        arrangeLongSyncCompletesNormally()
        SUT.startSync()
        // clear "arrangement" state
        longSyncDelegate.resetTestState()
    }

    private fun executeFirstEverSyncFlowAndFailRealtimeInit() {
        arrangeFirstEverSyncRequired()
        arrangeLongSyncCompletesNormally()
        realtimeSyncDelegate.shouldInitFail = true
        SUT.startSync()
        // clear "arrangement" state
        longSyncDelegate.resetTestState()
        realtimeSyncDelegate.shouldInitFail = false
    }

    private fun executeFirstSyncFlow() {
        arrangeHttpSyncCompletesNormally()
        SUT.startSync()
        // clear "arrangement" state
        httpSyncDelegate.resetTestState()
    }

    private fun executeFirstSyncFlowAndFailRealtimeInit() {
        arrangeHttpSyncCompletesNormally()
        realtimeSyncDelegate.shouldInitFail = true
        SUT.startSync()
        // clear "arrangement" state
        httpSyncDelegate.resetTestState()
        realtimeSyncDelegate.shouldInitFail = false
    }

    private fun arrangeFallbackState() {
        executeFirstSyncFlowAndFailRealtimeInit()
    }

    private fun arrangeRealtimeState() {
        executeFirstSyncFlow()
    }

    private fun arrangeRecoveryStateDuringFallback() {
        arrangeFallbackState()
        arrangeRecoveryStartsDuringHttpSync()
        SUT.startSync()
        httpSyncDelegate.resetTestState()
        longSyncDelegate.resetTestState()
    }

    private fun arrangeNoLoggedInUser() {
        loginStateDelegate.isLoggedIn = false
    }

    private fun arrangeFirstEverSyncRequired() {
        syncPreferencesDelegateTd.beforeFirstEverSync = true
    }

    private fun arrangeHttpSyncCompletesNormally() {
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
    }

    private fun arrangeLongSyncCompletesNormally() {
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
    }

    private fun arrangeAppInBackground() {
        appForegroundStateDelegate.isInForeground = false
    }

    private fun arrangeRecoveryStartsDuringHttpSync() {
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY
    }

    private fun arrangeRecoveryStartsAndEndsDuringHttpSync() {
        httpSyncDelegate.flowArrangement = HttpSyncDelegateTd.SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
    }

    private fun arrangeRecoveryStartsDuringRealtimeSync() {
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY
    }

    private fun arrangeRecoveryStartsAndEndsDuringRealtimeSync() {
        realtimeSyncDelegate.flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY
        longSyncDelegate.flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
    }

    private fun assertNoListenersRegistered() {
        assertTrue(httpSyncDelegate.listeners.isEmpty())
        assertTrue(realtimeSyncDelegate.listeners.isEmpty())
        assertTrue(longSyncDelegate.listeners.isEmpty())
        assertTrue(appForegroundStateDelegate.listeners.isEmpty())
    }

    private fun assertOnlySingleHttpSyncStarted() {
        assertThat(httpSyncDelegate.syncCallCounter, `is`(1))
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(0))
        assertThat(longSyncDelegate.syncCallCounter, `is`(0))
    }

    private fun assertOnlySingleLongSyncStarted() {
        assertThat(httpSyncDelegate.syncCallCounter, `is`(0))
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(0))
        assertThat(longSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.lastSyncWasFull, `is`(false))
    }

    private fun assertOnlySingleFullLongSyncStarted() {
        assertThat(httpSyncDelegate.syncCallCounter, `is`(0))
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(0))
        assertThat(longSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.lastSyncWasFull, `is`(true))
    }

    private fun assertRealtimeSyncStartedAndThenFullLongSyncStarted() {
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.lastSyncWasFull, `is`(true))
        assertTrue(longSyncDelegate.lastSyncNano > realtimeSyncDelegate.lastSyncNano)
        assertThat(httpSyncDelegate.syncCallCounter, `is`(0))
    }

    private fun assertHttpSyncStartedAndThenLongSyncStarted() {
        assertThat(httpSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.syncCallCounter, `is`(1))
        assertFalse(longSyncDelegate.lastSyncWasFull)
        assertTrue(httpSyncDelegate.lastSyncNano < longSyncDelegate.lastSyncNano)
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(0))
    }

    private fun assertRealtimeSyncStartedAndThenLongSyncStarted() {
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.syncCallCounter, `is`(1))
        assertFalse(longSyncDelegate.lastSyncWasFull)
        assertTrue(realtimeSyncDelegate.lastSyncNano < longSyncDelegate.lastSyncNano)
        assertThat(httpSyncDelegate.syncCallCounter, `is`(0))
    }

    private fun assertHttpSyncStartedAndThenFullLongSyncStarted() {
        assertThat(httpSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.syncCallCounter, `is`(1))
        assertThat(longSyncDelegate.lastSyncWasFull, `is`(true))
        assertTrue(httpSyncDelegate.lastSyncNano < longSyncDelegate.lastSyncNano)
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(0))
    }

    private fun assertOnlySingleRealtimeSyncStarted() {
        assertRealtimeSyncsStarted(1)
    }

    private fun assertRealtimeSyncsStarted(count: Int) {
        assertThat(httpSyncDelegate.syncCallCounter, `is`(0))
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(count))
        assertThat(longSyncDelegate.syncCallCounter, `is`(0))
    }

    private fun assertNoSyncStarted() {
        assertThat(httpSyncDelegate.syncCallCounter, `is`(0))
        assertThat(realtimeSyncDelegate.syncCallCounter, `is`(0))
        assertThat(longSyncDelegate.syncCallCounter, `is`(0))
    }

    // endregion helper methods --------------------------------------------------------------------


    // region helper classes -----------------------------------------------------------------------

    private class SyncPreferencesDelegateTd: SyncPreferencesDelegate {
        var beforeFirstEverSync = false

        override fun isWaitingForFirstEverSync(): Boolean {
            return beforeFirstEverSync
        }

        override fun setFirstEverSyncCompleted() {
            beforeFirstEverSync = false
        }
    }

    private class HttpSyncDelegateTd: HttpSyncDelegate {

        enum class SyncFlowArrangement {
            NO_COMPLETION,
            COMPLETE_NORMALLY,
            COMPLETE_UNSUCCESSFULLY,
            COMPLETE_TIMEOUT,
        }

        var lastSyncNano = 0L
        var flowArrangement = SyncFlowArrangement.NO_COMPLETION
        var syncCallCounter = 0

        val listeners = mutableListOf<HttpSyncDelegate.Listener>()

        init {
            resetTestState()
        }

        override fun startHttpSync() {
            syncCallCounter++
            lastSyncNano = System.nanoTime()
            when(flowArrangement) {
                SyncFlowArrangement.NO_COMPLETION -> { /* no completion */ }
                SyncFlowArrangement.COMPLETE_NORMALLY -> {
                    listeners.map { it.onHttpSyncCompleted() }
                }
                SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY -> {
                    listeners.map { it.onHttpResponseUnsuccessful() }
                }
                SyncFlowArrangement.COMPLETE_TIMEOUT -> {
                    listeners.map { it.onHttpSyncTimedOut() }
                }
            }
        }

        override fun registerListener(listener: HttpSyncDelegate.Listener) {
            listeners.add(listener)
        }

        override fun unregisterListener(listener: HttpSyncDelegate.Listener) {
            listeners.remove(listener)
        }

        fun resetTestState() {
            flowArrangement = SyncFlowArrangement.NO_COMPLETION
            syncCallCounter = 0
        }
    }

    private class RealtimeSyncDelegateTd: RealtimeSyncDelegate {

        enum class SyncFlowArrangement {
            COMPLETE_NORMALLY,
            COMPLETE_UNSUCCESSFULLY,
            COMPLETE_TIMEOUT,
        }

        var lastSyncNano = 0L
        var flowArrangement = SyncFlowArrangement.COMPLETE_NORMALLY
        var isOperational = true
        var syncCallCounter = 0

        val listeners = mutableListOf<RealtimeSyncDelegate.Listener>()

        var shouldInitFail = false

        override fun isRealTimeSyncOperational() = isOperational

        override fun initRealtimeSyncForOperation() {
            if (shouldInitFail) {
                isOperational = false
                listeners.map { it.onRealtimeInitFailed() }
            } else {
                isOperational = true
                listeners.map { it.onRealtimeInitSucceeded() }
            }
        }

        override fun shutDownRealtimeSync() {
            isOperational = false
        }

        override fun startRealtimeSync() {
            syncCallCounter++
            lastSyncNano = System.nanoTime()
            when(flowArrangement) {
                SyncFlowArrangement.COMPLETE_NORMALLY -> { /* no op */ }
                SyncFlowArrangement.COMPLETE_UNSUCCESSFULLY -> {
                    listeners.map { it.onRealtimeResponseUnsuccessful() }
                }
                SyncFlowArrangement.COMPLETE_TIMEOUT -> {
                    listeners.map { it.onRealtimeSyncTimedOut() }
                }
            }
        }

        override fun registerListener(listener: RealtimeSyncDelegate.Listener) {
            listeners.add(listener)
        }

        override fun unregisterListener(listener: RealtimeSyncDelegate.Listener) {
            listeners.remove(listener)
        }

        fun spuriousShutDown() {
            isOperational = false
            for (listener in listeners) {
                listener.onRealtimeShutDown()
            }
        }

        fun resetTestState() {
            flowArrangement = RealtimeSyncDelegateTd.SyncFlowArrangement.COMPLETE_NORMALLY
            syncCallCounter = 0
            isOperational = true
            lastSyncNano = 0
            shouldInitFail = false
        }
    }

    private class LoginStateDelegateTd: LoginStateDelegate {
        var isLoggedIn = true
        override fun isUserLoggedIn() = isLoggedIn
    }

    private class AppForegroundStateDelegateTd: AppForegroundStateDelegate {

        val listeners: MutableSet<AppForegroundStateDelegate.Listener> = CopyOnWriteArraySet()

        var isInForeground = true

        override fun isAppInForeground() = isInForeground

        override fun registerListener(listener: AppForegroundStateDelegate.Listener) {
            listeners.add(listener)
        }

        override fun unregisterListener(listener: AppForegroundStateDelegate.Listener) {
            listeners.remove(listener)
        }

        fun notifyAppTransitionedToForeground() {
            for (listener in listeners) {
                listener.onAppTransitionToForeground()
            }
        }

        fun notifyAppTransitionedToBackground() {
            for (listener in listeners) {
                listener.onAppTransitionToBackground()
            }
        }
    }

    private class LongSyncDelegateTd: LongSyncDelegate {

        enum class SyncFlowArrangement {
            NO_COMPLETION,
            COMPLETE_NORMALLY,
        }

        var lastSyncNano = 0L
        var flowArrangement = SyncFlowArrangement.NO_COMPLETION
        var syncCallCounter = 0
        var lastSyncWasFull = false

        val listeners = mutableListOf<LongSyncDelegate.Listener>()

        init {
            resetTestState()
        }

        override fun startLongSync(isFullSync: Boolean) {
            syncCallCounter++
            lastSyncWasFull = isFullSync
            lastSyncNano = System.nanoTime()
            when(flowArrangement) {
                SyncFlowArrangement.NO_COMPLETION -> { /* no completion */ }
                SyncFlowArrangement.COMPLETE_NORMALLY -> {
                    listeners.map { it.onLongSyncCompleted() }
                }
            }
        }

        override fun registerListener(listener: LongSyncDelegate.Listener) {
            listeners.add(listener)
        }

        override fun unregisterListener(listener: LongSyncDelegate.Listener) {
            listeners.remove(listener)
        }

        fun resetTestState() {
            flowArrangement = LongSyncDelegateTd.SyncFlowArrangement.NO_COMPLETION
            syncCallCounter = 0
            lastSyncWasFull = false
        }

    }
    // endregion helper classes --------------------------------------------------------------------
}
