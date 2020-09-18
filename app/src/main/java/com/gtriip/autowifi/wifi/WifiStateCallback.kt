package com.gtriip.autowifi.wifi

interface WifiStateCallback {
    fun onWifiStateEnabled()

    fun onSuccess()

    fun onFailed(errorCode: String, errorMessage: String?, errorDetails: Any?)
}