package com.gtriip.autowifi.wifi

interface WifiNetworkCallback {
    fun onAvailable()

    fun onUnavailable()

    fun onLost()
}