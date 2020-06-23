package com.gtriip.autowifi.wifi

interface WifiStateCallback {
    fun onWifiStateEnabled()

    fun onWifiStateDisabled()
}