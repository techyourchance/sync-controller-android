package com.techyourchance.synccontroller

/**
 * Currently, this class is just a wrapper for SyncState, but I decided to keep it in case
 * additional info will need to be added in the future
 */
data class SyncInfo(
        val syncState: SyncState,
)
