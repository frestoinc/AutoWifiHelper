package com.gtriip.autowifi.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.net.wifi.*
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.PatternMatcher
import android.util.Log
import androidx.annotation.RequiresApi
import com.gtriip.autowifi.domain.convertToQuotedString
import com.gtriip.autowifi.domain.isAndroidMarshmallowOrLater
import com.gtriip.autowifi.domain.isAndroidQorLater
import com.gtriip.autowifi.ui.ActivityCallback
import java.util.*

enum class Receiver {
    WIFI_SCAN, WIFI_STATE, WIFI_NETWORK
}

class HotelWifiHelper(
    private val appContext: Context,
    private val activityCallback: ActivityCallback
) :
    WifiHelper {

    companion object {
        private const val WIFI_SSID = "WIFI_SSID"
        private const val WIFI_ID = "WIFI_ID"
    }

    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val connectivityManager =
        appContext.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var map = mutableMapOf(
        Receiver.WIFI_SCAN to false,
        Receiver.WIFI_STATE to false,
        Receiver.WIFI_NETWORK to false
    )

    private var sendCallback: Boolean = true

    private var config = mutableMapOf(WIFI_SSID to "", WIFI_ID to -1)

    private val timer = object : CountDownTimer(15000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            //not handled
        }

        override fun onFinish() {
            activityCallback.onFailed("WifiException", "unable to connect to network", "")
        }
    }

    private val wifiScanReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                Handler().postDelayed({
                    activityCallback.onScanResult(wifiManager.scanResults)
                }, 3000)
            }
        }
    }

    @Suppress("DEPRECATION")
    private val wifiStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    when (intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN
                    )) {
                        WifiManager.WIFI_STATE_ENABLED -> {
                            Log.d("TAG", "SSID: ${wifiManager.connectionInfo.ssid}")
                            if (sendCallback) activityCallback.onWifiStateEnabled()
                        }
                    }
                }

                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    if (!sendCallback) {
                        val info: NetworkInfo? =
                            intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
                        if (info != null && info.state == NetworkInfo.State.CONNECTED) {
                            unregisterNetworkReceiver()
                            timer.cancel()
                            Log.d(
                                "TAG",
                                "SSID: ${wifiManager.connectionInfo.ssid} v ${config[WIFI_SSID]}"
                            )
                            Log.d(
                                "TAG",
                                "SSID: ${wifiManager.connectionInfo.networkId} v ${config[WIFI_ID]}"
                            )
                            if (doesSSIDMatch(config[WIFI_SSID] as String) || config[WIFI_ID] as Int == wifiManager.connectionInfo.networkId) {
                                activityCallback.onSuccess()
                            } else {
                                activityCallback.onFailed(
                                    "WifiException",
                                    "unable to connect to network",
                                    ""
                                )
                            }
                            sendCallback = true
                        }
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
                Log.d("TAG", "onUnavailable")
            }
        }

    override fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    override fun registerNetworkReceiver() {
        map[Receiver.WIFI_STATE] = true
        val intentFilter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        appContext.registerReceiver(
            wifiStateReceiver, intentFilter
        )
    }

    override fun unregisterNetworkReceiver() {
        if (map[Receiver.WIFI_STATE]!!) {
            map[Receiver.WIFI_STATE] = false
            appContext.unregisterReceiver(wifiStateReceiver)
        }

        if (map[Receiver.WIFI_NETWORK]!!) {
            map[Receiver.WIFI_NETWORK] = false
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }


    @Suppress("DEPRECATION")
    override fun turnOnWifi() {
        wifiManager.isWifiEnabled = true
    }

    @Suppress("DEPRECATION")
    override fun startScan() {
        appContext.registerReceiver(
            wifiScanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        map[Receiver.WIFI_SCAN] = true
        if (!wifiManager.startScan()) {
            stopScan()
            activityCallback.onFailed("WifiScan", "Not able to scan devuces", "")
        }
    }

    override fun stopScan() {
        if (map[Receiver.WIFI_SCAN]!!) {
            map[Receiver.WIFI_SCAN] = false
            appContext.unregisterReceiver(wifiScanReceiver)
        }
    }

    override fun buildAutoWifiConnection(result: ScanResult, pwd: String) {
        Log.d("TAG", "START AutoWifiConnection")
        if (isAndroidQorLater()) {
            autoWifiConnectQOrLater(result, pwd)
        } else {
            autoWifiConnectPreQ(result, pwd)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun autoWifiConnectQOrLater(result: ScanResult, pwd: String) {
        map[Receiver.WIFI_NETWORK] = true
        connectivityManager.requestNetwork(buildNetworkRequest(result, pwd), networkCallback)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun autoWifiConnectPreQ(result: ScanResult, pwd: String) {
        var configuration: WifiConfiguration? = null
        var i = -1
        for (configured in wifiManager.configuredNetworks) {
            if (result.SSID == configured.SSID) {
                Log.d("TAG", "getting save configuration")
                configuration = configured
                i = configured.networkId
                break
            }
        }

        if (null == configuration) {
            Log.d("TAG", "setting up configuration")
            configuration = getConfiguration(result, pwd)
            i = wifiManager.addNetwork(configuration)
        }

        if (i == -1) {
            activityCallback.onFailed("WifiException", "Unable to add configuration", "")
            return
        }
        Log.d("TAG", "configuration id: $i")

        config[WIFI_SSID] = result.SSID
        config[WIFI_ID] = i

        wifiManager.disconnect()
        wifiManager.enableNetwork(i, true)
        wifiManager.reconnect()
        sendCallback = false
        timer.start()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getSpecificWifi(ssid: String, pwd: String): WifiNetworkSpecifier {
        val builder = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher(ssid, PatternMatcher.PATTERN_PREFIX))
        if (pwd.isNotEmpty()) {
            builder.setWpa2Passphrase(pwd)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getWifiSuggestion(ssid: String, pwd: String): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
        if (pwd.isNotEmpty()) {
            builder.setWpa2Passphrase(pwd)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun getWifiSuggestionList(
        ssid: String,
        pwd: String
    ): ArrayList<WifiNetworkSuggestion> {
        val list =
            ArrayList<WifiNetworkSuggestion>()
        list.add(getWifiSuggestion(ssid, pwd))
        return list
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun buildNetworkRequest(result: ScanResult, pwd: String): NetworkRequest {
        return NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            //.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(getSpecificWifi(result.SSID, pwd))
            .build()
    }

    override fun doesSSIDMatch(ssid: String): Boolean {
        val connectedSSID = wifiManager.connectionInfo.ssid
        return connectedSSID == convertToQuotedString(ssid) || connectedSSID.contains(ssid)
    }

    @Suppress("DEPRECATION")
    private fun getConfiguration(result: ScanResult, password: String): WifiConfiguration {
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

        if (password.isNotEmpty()) {
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
            Log.d("TAG", "password is empty")
        }

        return config
    }
}