package com.techyourchance.synccontroller

interface SyncPreferencesDelegate {
    fun setFirstEverSyncCompleted()
    fun isWaitingForFirstEverSync(): Boolean
}
