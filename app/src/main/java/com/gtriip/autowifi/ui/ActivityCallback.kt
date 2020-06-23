package com.gtriip.autowifi.ui

import com.gtriip.autowifi.wifi.WifiNetworkCallback
import com.gtriip.autowifi.wifi.WifiScanResultCallback
import com.gtriip.autowifi.wifi.WifiStateCallback

interface ActivityCallback : WifiStateCallback,
    WifiNetworkCallback, WifiScanResultCallback