package com.techyourchance.synccontroller

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class orchestrates the data sync flows between the client and the server.
 * IMPLEMENTATION NOTE: this class (i.e. all its methods) must be fully thread-safe
 */
class SyncController(
        private val loginStateDelegate: LoginStateDelegate,
        private val syncPreferencesDelegate: SyncPreferencesDelegate,
        private val realtimeSyncDelegate: RealtimeSyncDelegate,
        private val httpSyncDelegate: HttpSyncDelegate,
        private val longSyncDelegate: LongSyncDelegate,
        private val appForegroundStateDelegate: AppForegroundStateDelegate,
        private val loggerDelegate: LoggerDelegate,
) {

    private val lock = ReentrantLock(true)

    private var syncState: SyncState = SyncState.IDLE

    private var isFallbackSyncInProgress = false

    private val httpListener = object : HttpSyncDelegate.Listener {
        override fun onHttpSyncCompleted() {
            this@SyncController.onHttpSyncCompleted()
        }

        override fun onHttpResponseUnsuccessful() {
            this@SyncController.onHttpResponseUnsuccessful()
        }

        override fun onHttpSyncTimedOut() {
            this@SyncController.onHttpSyncTimedOut()
        }
    }

    private val realtimeListener = object : RealtimeSyncDelegate.Listener {
        override fun onRealtimeInitSucceeded() {
            lock.withLock {
                log("onRealtimeInitSucceeded()")
                setState(SyncState.REALTIME)
            }
        }

        override fun onRealtimeInitFailed() {
            lock.withLock {
                log("onRealtimeInitFailed()")
                setState(SyncState.FALLBACK)
            }
        }

        override fun onRealtimeResponseUnsuccessful() {
            lock.withLock {
                log("onRealtimeResponseUnsuccessful()")
                startRecoverySync()
            }
        }

        override fun onRealtimeSyncTimedOut() {
            lock.withLock {
                log("onRealtimeSyncTimedOut()")
                realtimeSyncDelegate.shutDownRealtimeSync()
                startPostTimeoutSync()
            }
        }

        override fun onRealtimeShutDown() {
            lock.withLock {
                log("onRealtimeShutDown()")
                if (syncState == SyncState.REALTIME) {
                    setState(SyncState.FALLBACK)
                }
            }
        }
    }

    private val longSyncListener = object : LongSyncDelegate.Listener {
        override fun onLongSyncCompleted() {
            this@SyncController.onLongSyncCompleted()
        }
    }

    private val appForegroundListener = object : AppForegroundStateDelegate.Listener {
        override fun onAppTransitionToForeground() {
            lock.withLock {
                log("onAppTransitionToForeground()")
                startSync()
            }
        }

        override fun onAppTransitionToBackground() {
            lock.withLock {
                log("onAppTransitionToBackground()")
                if (syncState == SyncState.REALTIME) {
                    realtimeSyncDelegate.shutDownRealtimeSync()
                    setState(SyncState.FALLBACK)
                }
            }
        }
    }

    fun getSyncInfo(): SyncInfo {
        return lock.withLock {
            SyncInfo(
                    syncState,
            )
        }
    }

    /**
     * Initiate new sync, if the state allows for new sync to be initiated, according to
     * sync algorithm specification
     */
    fun startSync() {
        lock.withLock {
            log("startSync(); state: $syncState")

            if (!loginStateDelegate.isUserLoggedIn()) {
                log("no logged in user - sync request ignored")
                return
            }

            when (syncState) {
                SyncState.IDLE -> {
                    if (syncPreferencesDelegate.isWaitingForFirstEverSync()) {
                        startFirstEverSync()
                    } else {
                        startFirstSync()
                    }
                }
                SyncState.REALTIME -> {
                    startRealtimeSync()
                }
                SyncState.FALLBACK -> {
                    if (isFallbackSyncInProgress) {
                        log("fallback sync is already in progress - sync request ignored")
                    } else {
                        startFallbackSync()
                    }
                }
                else -> {
                    log("sync request ignored")
                }
            }
        }
    }

    private fun setState(newState: SyncState) {
        lock.withLock {
            if (syncState == newState) {
                return
            }
            val oldState = syncState
            log("setState(); old state: $oldState; new state: $newState")
            syncState = newState
            if (oldState == SyncState.IDLE) {
                registerListeners()
            }
            if (newState == SyncState.IDLE) {
                unregisterListeners()
            }
        }
    }

    private fun registerListeners() {
        httpSyncDelegate.registerListener(httpListener)
        realtimeSyncDelegate.registerListener(realtimeListener)
        longSyncDelegate.registerListener(longSyncListener)
        appForegroundStateDelegate.registerListener(appForegroundListener)
    }

    private fun unregisterListeners() {
        httpSyncDelegate.unregisterListener(httpListener)
        realtimeSyncDelegate.unregisterListener(realtimeListener)
        longSyncDelegate.unregisterListener(longSyncListener)
        appForegroundStateDelegate.unregisterListener(appForegroundListener)
    }

    private fun startFirstEverSync() {
        lock.withLock {
            setState(SyncState.FIRST_EVER_SYNC)
            startLongSync(true)
        }
    }

    private fun startFirstSync() {
        lock.withLock {
            setState(SyncState.FIRST_SYNC)
            startHttpSync()
        }
    }

    private fun startFallbackSync() {
        lock.withLock {
            setState(SyncState.FALLBACK)
            isFallbackSyncInProgress = true
            startHttpSync()
        }
    }

    private fun startHttpSync() {
        httpSyncDelegate.startHttpSync()
    }

    private fun startLongSync(isFullSync: Boolean) {
        longSyncDelegate.startLongSync(isFullSync)
    }

    private fun startRealtimeSync() {
        lock.withLock {
            setState(SyncState.REALTIME)
            realtimeSyncDelegate.startRealtimeSync()
        }
    }

    private fun onLongSyncCompleted() {
        lock.withLock {
            log("onLongSyncCompleted()")
            if (syncPreferencesDelegate.isWaitingForFirstEverSync()) {
                log("first ever sync completed")
                syncPreferencesDelegate.setFirstEverSyncCompleted()
            }

            isFallbackSyncInProgress = false

            initRealtimeSyncOrGoToFallback()
        }
    }

    private fun onHttpSyncCompleted() {
        lock.withLock {
            log("onHttpSyncCompleted()")
            if (syncPreferencesDelegate.isWaitingForFirstEverSync()) {
                log("first ever sync completed")
                syncPreferencesDelegate.setFirstEverSyncCompleted()
            }

            isFallbackSyncInProgress = false

            initRealtimeSyncOrGoToFallback()

        }
    }

    private fun onHttpResponseUnsuccessful() {
        lock.withLock {
            log("onHttpResponseUnsuccessful()")
            isFallbackSyncInProgress = false
            startRecoverySync()
        }
    }


    private fun onHttpSyncTimedOut() {
        lock.withLock {
            log("onHttpSyncTimedOut()")
            isFallbackSyncInProgress = false
            startPostTimeoutSync()
        }
    }

    private fun startPostTimeoutSync() {
        lock.withLock {
            setState(SyncState.POST_TIMEOUT_SYNC)
            longSyncDelegate.startLongSync(false)
        }
    }

    private fun initRealtimeSyncOrGoToFallback() {
        lock.withLock {
            log("initRealtimeSyncOrGoToFallback()")
            
            if (syncState == SyncState.REALTIME) {
                log("already in REALTIME state - init aborted")
                return
            }

            if (appForegroundStateDelegate.isAppInForeground()) {
                log("initializing realtime sync")
                realtimeSyncDelegate.initRealtimeSyncForOperation()
            } else {
                log("app is in background - postponing realtime sync init")
                setState(SyncState.FALLBACK)
            }
        }
    }

    private fun startRecoverySync() {
        lock.withLock {
            log("startRecoverySync()")
            if (syncState == SyncState.REALTIME) {
                realtimeSyncDelegate.shutDownRealtimeSync()
            }
            setState(SyncState.RECOVERY_SYNC)
            longSyncDelegate.startLongSync(true)
        }
    }

    private fun onHttpRecoverySyncEnded() {
        lock.withLock {
            log("onHttpRecoverySyncEnded")
            setState(SyncState.FALLBACK)
        }
    }

    private fun log(message: String) {
        loggerDelegate.d(TAG, message)
    }

    /**
     * This method must be called when we clear user's state (e.g. logout)
     */
    fun reset() {
        lock.withLock {
            log("reset()")
            setState(SyncState.IDLE)
            isFallbackSyncInProgress = false
            realtimeSyncDelegate.shutDownRealtimeSync()
        }
    }

    companion object {
        private const val TAG = "SyncController"
    }
}
