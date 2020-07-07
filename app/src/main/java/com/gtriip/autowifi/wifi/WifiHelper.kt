package com.gtriip.autowifi.wifi

import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import java.util.*

interface WifiHelper {
    fun turnOnWifi()

    fun isWifiEnabled(): Boolean

    fun isAutoWifiPermissionGranted(): Boolean

    fun setAutoWifiPermission(granted: Boolean)

    fun isConfigured(): Boolean

    fun setConfigured(set: Boolean)

    fun registerNetworkReceiver()

    fun unRegisterNetwork()

    fun getSpecificWifi(ssid: String, pwd: String?): WifiNetworkSpecifier

    fun getWifiSuggestion(ssid: String, pwd: String?): WifiNetworkSuggestion

    fun getWifiSuggestionList(ssid: String, pwd: String?): ArrayList<WifiNetworkSuggestion>

    fun buildNetworkRequest(ssid: String, pwd: String?): NetworkRequest

    fun buildAutoWifiConnection(result: ScanResult, pwd: String?)

    fun startScan()

    fun stopScan()

    fun doesSSIDMatch(ssid: String): Boolean
}