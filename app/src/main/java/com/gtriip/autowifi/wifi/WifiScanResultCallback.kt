package com.gtriip.autowifi.wifi

import android.net.wifi.ScanResult

interface WifiScanResultCallback {
    fun onScanResult(result: List<ScanResult>)
}