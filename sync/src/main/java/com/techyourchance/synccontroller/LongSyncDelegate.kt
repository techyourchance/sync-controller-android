package com.techyourchance.synccontroller

interface LongSyncDelegate {

    interface Listener {
        fun onLongSyncCompleted()
    }

    /**
     * Call to this method will start a special type of sync, consisting of multiple steps. First,
     * the app will ask the server to prepare sync response asynchronously. Then,
     * the app will start polling the server until the server prepares the response and returns
     * it, or until a timeout condition is reached.
     * @param isFullSync if true, app's data will be re-initialized with server's data, deleting all
     *                   pre-existing data in the process.
     */
    fun startLongSync(isFullSync: Boolean)

    fun registerListener(listener: Listener)
    fun unregisterListener(listener: Listener)
}