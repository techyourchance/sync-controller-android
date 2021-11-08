package com.techyourchance.synccontroller

interface RealtimeSyncDelegate {

    interface Listener {
        fun onRealtimeInitSucceeded()
        fun onRealtimeInitFailed()
        fun onRealtimeResponseUnsuccessful()
        fun onRealtimeSyncTimedOut()
        fun onRealtimeShutDown()
    }

    fun initRealtimeSyncForOperation()
    fun shutDownRealtimeSync()
    fun isRealTimeSyncOperational(): Boolean

    fun startRealtimeSync()

    fun registerListener(listener: Listener)
    fun unregisterListener(listener: Listener)
}
