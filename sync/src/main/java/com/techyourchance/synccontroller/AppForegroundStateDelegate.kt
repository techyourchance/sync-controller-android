package com.techyourchance.synccontroller

interface AppForegroundStateDelegate {

    interface Listener {
        fun onAppTransitionToForeground()
        fun onAppTransitionToBackground()
    }

    fun isAppInForeground(): Boolean
    fun registerListener(listener: Listener)
    fun unregisterListener(listener: Listener)
}