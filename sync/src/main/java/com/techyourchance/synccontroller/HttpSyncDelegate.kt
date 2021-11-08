package com.techyourchance.synccontroller

interface HttpSyncDelegate {

    interface Listener {
        fun onHttpSyncCompleted()
        fun onHttpResponseUnsuccessful()
        fun onHttpSyncTimedOut()
    }

    fun startHttpSync()
    fun registerListener(listener: Listener)
    fun unregisterListener(listener: Listener)
}