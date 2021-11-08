package com.techyourchance.synccontroller

enum class SyncState {
    /**
     * Initial state
     */
    IDLE,

    /**
     * The app performs the first full sync after login
     */
    FIRST_EVER_SYNC,

    /**
     * The app performs the first sync after a session start
     */
    FIRST_SYNC,

    /**
     * Realtime sync operational
     */
    REALTIME,

    /**
     * Realtime sync not operational
     */
    FALLBACK,

    /**
     * Sync attempt resulted in timeout, so the app attempts to sync using alternative mechanism
     */
    POST_TIMEOUT_SYNC,

    /**
     * The app performs full sync from scratch following "fail" indication from the server
     */
    RECOVERY_SYNC,
}