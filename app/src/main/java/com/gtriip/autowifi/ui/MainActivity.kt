package com.gtriip.autowifi.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.gtriip.autowifi.R
import com.gtriip.autowifi.databinding.ActivityMainBinding
import com.gtriip.autowifi.domain.*
import com.gtriip.autowifi.location.HotelLocationHelper
import com.gtriip.autowifi.wifi.HotelWifiHelper

class MainActivity : AppCompatActivity(), DialogListener, ActivityCallback {

    companion object {
        const val WIFI_SSID = "YOUR_SSID" //TODO CHANGE

        const val WIFI_PWD = "wipvd0ksdw" // TODO CHANGE
    }

    private lateinit var binder: ActivityMainBinding

    private lateinit var locationHelper: HotelLocationHelper

    private lateinit var wifiHelper: HotelWifiHelper

    override fun onSuccess() {
        logText("ALL OK", false)
    }

    override fun onFailed(errorCode: String, errorMessage: String?, errorDetails: Any?) {
        logText("$errorCode: $errorMessage", true)
    }

    override fun onResume() {
        super.onResume()
        preCheckConditions()
    }

    override fun onDestroy() {
        wifiHelper.unregisterNetworkReceiver()
        super.onDestroy()
    }

    private fun logText(text: String, error: Boolean) {
        Log.e("MainActivity", text)
        val spannableString = SpannableStringBuilder("\n" + text)
        val color = ForegroundColorSpan(getColor(android.R.color.holo_red_dark))
        if (error) {
            spannableString.setSpan(color, 0, spannableString.length, 0)
        }

        runOnUiThread {
            binder.text.append(spannableString)
            binder.scrollview.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun preCheckConditions() {
        logText("Checking whether user already connect to wifi ssid ", false)
        if (wifiHelper.doesSSIDMatch(WIFI_SSID)) {
            onSuccess()
            return
        }

        logText("Checking whether wifi service is on", false)
        if (!wifiHelper.isWifiEnabled()) {
            turnOnWifiSettings()
            return
        }

        logText("Checking for location permission", false)
        if (!locationHelper.locationPermissionsGranted()) {
            requestLocationPermission()
            return
        }

        logText("Checking whether location service is on", false)
        if (!locationHelper.isLocationEnabled()) {
            showLocationOnRationale()
            return
        }
        startAutoWifiProcess()
    }

    private fun startAutoWifiProcess() {
        logText("START autoWifiConnect for SSID: $WIFI_SSID", false)
        if (wifiHelper.doesSSIDMatch(WIFI_SSID)) {
            onSuccess()
            return
        }

        resetNetworkReceiver()
    }

    private fun resetNetworkReceiver() {
        wifiHelper.unregisterNetworkReceiver()
        wifiHelper.registerNetworkReceiver()
    }

    private fun turnOnLocationSettings() {
        logText("Turning on location", false)
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivityForResult(
            intent,
            REQUEST_CODE_FOR_TURN_ON_LOCATION
        )
    }

    private fun turnOnWifiSettings() {
        wifiHelper.registerNetworkReceiver()
        if (isAndroidQorLater()) {
            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
            startActivityForResult(
                panelIntent,
                REQUEST_CODE_FOR_SWITCH_ON_WIFI
            )
        } else {
            wifiHelper.turnOnWifi()
        }
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_CODE_FOR_LOCATION_PERMISSION
        )
        return
    }

    override fun onWifiStateEnabled() {
        if (wifiHelper.doesSSIDMatch(WIFI_SSID)) {
            onSuccess()
        } else {
            wifiHelper.startScan()
        }
    }

    override fun onAvailable() {
        logText("NetworkCallback onAvailable", false)
        if (wifiHelper.doesSSIDMatch(WIFI_SSID)) {
            onSuccess()
        } else {
            logText("ssid does not match", true)
        }
    }

    override fun onScanResult(list: List<ScanResult>) {
        wifiHelper.stopScan()
        list.forEach {
            logText("scan result ssid: ${it.SSID}", false)
        }
        val (match, _) = list.partition { it.SSID == WIFI_SSID }
        if (match.isEmpty()) {
            onFailed("WifiException", "ssid not in range", "")
        } else {
            logText("Call auto wifi connect", false)
            wifiHelper.buildAutoWifiConnection(match.first(), WIFI_PWD)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binder = DataBindingUtil.setContentView(
            this,
            R.layout.activity_main
        )
        logText("OnCreate", false)

        locationHelper = HotelLocationHelper(this)
        wifiHelper = HotelWifiHelper(this, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.e("TAG", "requestCode: $requestCode, resultCode: $resultCode")
        when (requestCode) {
            REQUEST_CODE_FOR_SWITCH_ON_WIFI -> when (resultCode) {
                Activity.RESULT_OK -> preCheckConditions()
                else -> {
                    logText("WIFI Permission Denied", true)
                    showWifiDialogRationale()
                }
            }
            REQUEST_CODE_FOR_TURN_ON_LOCATION -> preCheckConditions()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_FOR_LOCATION_PERMISSION) {
            if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
                logText("Location Permission Denied", true)
                showLocationPermissionDialog()
            } else {
                logText("Permission Granted", false)
                preCheckConditions()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onPositiveButtonClicked(type: DialogType) {
        when (type) {
            DialogType.AUTO_WIFI_RATIONALE -> requestAutoWifiConnectPermission()
            DialogType.LOCATION_WIFI_RATIONALE -> turnOnLocationSettings()
            DialogType.LOCATION_PERMISSION_RATIONALE -> requestLocationPermission()
            DialogType.AUTO_WIFI_PERMISSION, DialogType.WIFI_DISABLED -> preCheckConditions()
        }
    }

    override fun onNegativeButtonClicked(type: DialogType) {
        when (type) {
            DialogType.AUTO_WIFI_PERMISSION -> {
                showAutoWifiConnectionRationale()
            }
            else -> onFailed("Denied", "Permission Denied", "")
        }
    }

    override fun onCancelled(type: DialogType) {
        when (type) {
            DialogType.AUTO_WIFI_PERMISSION -> {
                showAutoWifiConnectionRationale()
            }
            else -> onFailed("Denied", "Permission Denied", "")
        }
    }

    private fun showLocationPermissionDialog() {
        createDialog(DialogType.LOCATION_PERMISSION_RATIONALE, this).show()
    }

    private fun showLocationOnRationale() {
        createDialog(DialogType.LOCATION_WIFI_RATIONALE, this).show()
    }

    private fun showWifiDialogRationale() {
        createDialog(DialogType.WIFI_DISABLED, this).show()
    }

    private fun requestAutoWifiConnectPermission() {
        createDialog(DialogType.AUTO_WIFI_PERMISSION, this).show()
    }

    private fun showAutoWifiConnectionRationale() {
        createDialog(DialogType.AUTO_WIFI_RATIONALE, this).show()
    }
}