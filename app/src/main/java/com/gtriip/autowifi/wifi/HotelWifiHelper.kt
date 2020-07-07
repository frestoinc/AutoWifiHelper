package com.gtriip.autowifi.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.*
import android.os.Build
import android.os.Handler
import android.os.PatternMatcher
import android.util.Log
import androidx.annotation.RequiresApi
import com.gtriip.autowifi.domain.convertToQuotedString
import com.gtriip.autowifi.domain.isAndroidMarshmallowOrLater
import com.gtriip.autowifi.domain.isAndroidNougatOrLater
import com.gtriip.autowifi.domain.isAndroidQorLater
import com.gtriip.autowifi.ui.ActivityCallback
import java.util.*

class HotelWifiHelper(
    private val appContext: Context,
    private val activityCallback: ActivityCallback
) :
    WifiHelper {

    companion object {
        const val preferenceName = "wificonfiguration"
        const val isAutoWifi = "isAutoWifi"
        const val isConfigured = "isConfigured"
    }

    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val connectivityManager =
        appContext.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


    private val sharedPreferences =
        appContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    private val wifiScanReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                Handler().postDelayed({
                    activityCallback.onScanResult(wifiManager.scanResults)
                }, 2000)
            }
        }
    }

    private val wifiStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.e("TAG", "action:${intent.action}")
            when (intent.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    when (intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN
                    )) {
                        WifiManager.WIFI_STATE_ENABLED -> {
                            Log.e("wifiStateReceiver", "SSID: ${wifiManager.connectionInfo.ssid}")
                            activityCallback.onWifiStateEnabled()
                        }
                        WifiManager.WIFI_STATE_DISABLED -> activityCallback.onWifiStateDisabled()
                    }
                }
            }
        }
    }

    private val networkCallback: ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (isAndroidMarshmallowOrLater()) {
                    connectivityManager.bindProcessToNetwork(network)
                }
                activityCallback.onAvailable()
            }

            override fun onUnavailable() {
                super.onUnavailable()
                activityCallback.onUnavailable()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                activityCallback.onLost()
            }
        }

    override fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    override fun isAutoWifiPermissionGranted(): Boolean {
        return sharedPreferences.getBoolean(isAutoWifi, false)
    }

    override fun setAutoWifiPermission(granted: Boolean) {
        sharedPreferences.edit().putBoolean(isAutoWifi, granted).apply()
    }

    override fun isConfigured(): Boolean {
        return sharedPreferences.getBoolean(isConfigured, false)
    }

    override fun setConfigured(set: Boolean) {
        sharedPreferences.edit().putBoolean(isConfigured, set).apply()
    }

    override fun registerNetworkReceiver() {
        appContext.registerReceiver(
            wifiStateReceiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        )
    }

    @Throws(Exception::class)
    override fun unRegisterNetwork() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        appContext.unregisterReceiver(wifiStateReceiver)

    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getSpecificWifi(ssid: String, pwd: String?): WifiNetworkSpecifier {
        val builder = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher(ssid, PatternMatcher.PATTERN_PREFIX))
        if (pwd != null) {
            builder.setWpa2Passphrase(pwd)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getWifiSuggestion(ssid: String, pwd: String?): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
        if (pwd != null) {
            builder.setWpa2Passphrase(pwd)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getWifiSuggestionList(
        ssid: String,
        pwd: String?
    ): ArrayList<WifiNetworkSuggestion> {
        val list =
            ArrayList<WifiNetworkSuggestion>()
        list.add(getWifiSuggestion(ssid, pwd))
        return list
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun buildNetworkRequest(ssid: String, pwd: String?): NetworkRequest {
        return NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(getSpecificWifi(ssid, pwd))
            .build()
    }

    override fun turnOnWifi() {
        wifiManager.isWifiEnabled = true
    }

    override fun startScan() {
        if (wifiManager.startScan()) {
            Log.e("TAG", "start scanning")
            appContext.registerReceiver(
                wifiScanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            )
        } else {
            Log.e("TAG", "not scanning")
            activityCallback.onScanResult(arrayListOf())
        }
    }

    override fun stopScan() {
        appContext.unregisterReceiver(wifiScanReceiver)
    }

    override fun doesSSIDMatch(ssid: String): Boolean {
        return wifiManager.connectionInfo.ssid == convertToQuotedString(ssid)
    }

    override fun buildAutoWifiConnection(result: ScanResult, pwd: String?) {
        stopScan()
        Log.e("TAG", "START AutoWifiConnection")
        if (isAndroidQorLater()) {
            autoWifiConnectQOrLater(result.SSID, pwd)
        } else {
            autoWifiConnectPreQ(result, pwd)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    internal fun autoWifiConnectQOrLater(ssid: String, pwd: String?) {
        connectivityManager.requestNetwork(buildNetworkRequest(ssid, pwd), networkCallback)
    }

    private fun autoWifiConnectPreQ(result: ScanResult, pwd: String?) {
        var configuration: WifiConfiguration? = null
        for (configured in wifiManager.configuredNetworks) {
            if (result.SSID == configured.SSID) {
                Log.e("autoWifiConnectQ", "getting save configuration")
                configuration = configured
                break
            }
        }

        if (null == configuration) {
            Log.e("autoWifiConnectQ", "setting up configuration")
            configuration = getConfiguration(result, pwd)

        }

        val i = wifiManager.addNetwork(configuration)
        Log.e("TAG", "addNetwork status: $i")
        wifiManager.disconnect()
        wifiManager.enableNetwork(i, true)
        wifiManager.reconnect()
        if (isAndroidNougatOrLater()) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            setConfigured(true)
            activityCallback.onWifiStateEnabled()
        }
    }

    private fun getConfiguration(result: ScanResult, password: String?): WifiConfiguration {
        val capabilities = result.capabilities.substring(1, result.capabilities.indexOf(']') - 1)
            .split('-')
            .toSet()
        val auth = capabilities.elementAtOrNull(0) ?: ""
        val keyManagement = capabilities.elementAtOrNull(1) ?: ""
        val pairwiseCipher = capabilities.elementAtOrNull(2) ?: ""

        val config = WifiConfiguration()
        config.SSID = convertToQuotedString(result.SSID)

        if (auth.contains("WPA") || auth.contains("WPA2")) {
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
        }

        if (auth.contains("EAP"))
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.LEAP)
        else if (auth.contains("WPA") || auth.contains("WPA2"))
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
        else if (auth.contains("WEP"))
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)

        if (keyManagement.contains("IEEE802.1X"))
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
        else if (auth.contains("WPA") && keyManagement.contains("EAP"))
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
        else if (auth.contains("WPA") && keyManagement.contains("PSK"))
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        else if (auth.contains("WPA2") && keyManagement.contains("PSK"))
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        else config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)

        if (pairwiseCipher.contains("CCMP") || pairwiseCipher.contains("TKIP")) {
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
        }

        if (password != null && password.isNotEmpty()) {
            if (auth.contains("WEP")) {
                if (password.matches("\\p{XDigit}+".toRegex())) {
                    config.wepKeys[0] = password
                } else {
                    config.wepKeys[0] = convertToQuotedString(password)
                }
                config.wepTxKeyIndex = 0
            } else {
                config.preSharedKey = convertToQuotedString(password)
            }
        } else {
            Log.e("getConfiguration", "password is null")
        }

        return config
    }
}