package com.gtriip.autowifi.wifi

interface WifiNetworkCallback {
    fun onAvailable(ssid: String)

    fun onUnavailable()

    fun onLost()
}