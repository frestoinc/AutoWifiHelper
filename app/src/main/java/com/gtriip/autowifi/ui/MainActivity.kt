package com.gtriip.autowifi.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.gtriip.autowifi.R
import com.gtriip.autowifi.databinding.ActivityMainBinding
import com.gtriip.autowifi.domain.*
import com.gtriip.autowifi.location.HotelLocationHelper
import com.gtriip.autowifi.wifi.HotelWifiHelper

/** first we scan
if scan result is empty means ssid is not in range. terminate
then we compare the scan result with the configuration in wifimanger
if it matches then we use the existing configuration
else base on the scanresult we determine the configuration by parsing the result security type
then we connect**/

class MainActivity : AppCompatActivity(),
    DialogListener,
    ActivityCallback {

    companion object {
        const val WIFI_SSID = "GTRIIP"

        const val WIFI_PWD = "wipvd0ksdw"
    }

    private lateinit var binder: ActivityMainBinding

    private lateinit var locationHelper: HotelLocationHelper

    private lateinit var wifiHelper: HotelWifiHelper

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

    override fun onResume() {
        super.onResume()
        preCheckConditions()
    }

    override fun onDestroy() {
        try {
            wifiHelper.unRegisterNetwork()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        logText("Checking whether user permits auto-wifi connection", false)
        if (!wifiHelper.isAutoWifiPermissionGranted()) {
            requestAutoWifiConnectPermission()
            return
        }

        if (wifiHelper.isConfigured()) {
            postConfiguration()
        } else {
            preConfiguration()
        }

    }

    private fun postConfiguration() {
        logText("START postConfiguration autoWifiConnect for SSID: $WIFI_SSID", false)
        if (!wifiHelper.isWifiEnabled()) {
            turnOnWifiSettings()
            return
        }

        if (!locationHelper.isLocationEnabled()) {
            logText("Location is off", true)
            showLocationOnRationale()
            return
        }
        resetNetworkReceiver()
    }

    private fun preConfiguration() {
        logText("START preConfiguration autoWifiConnect for SSID: $WIFI_SSID", false)
        if (!locationHelper.locationPermissionsGranted()) {
            requestLocationPermission()
            return
        }

        if (!locationHelper.isLocationEnabled()) {
            logText("Location is off", true)
            showLocationOnRationale()
            return
        }

        if (!wifiHelper.isWifiEnabled()) {
            turnOnWifiSettings()
            return
        }

        resetNetworkReceiver()
    }

    private fun resetNetworkReceiver() {
        try {
            wifiHelper.unRegisterNetwork()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wifiHelper.registerNetworkReceiver()
        }
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
        logText("onWifiStateEnabled", false)
        if (!wifiHelper.isConfigured()) {
            wifiHelper.startScan()
        } else {
            Handler().postDelayed({
                when {
                    wifiHelper.doesSSIDMatch(WIFI_SSID) -> {
                        proceed()
                    }
                    isAndroidQorLater() -> {
                        wifiHelper.autoWifiConnectQOrLater(WIFI_SSID, WIFI_PWD)
                    }
                    else -> {
                        wifiHelper.setConfigured(false)
                        preConfiguration()
                    }
                }
            }, 3000)
        }
    }

    override fun onWifiStateDisabled() {
        logText("wifi is disabled", true)
    }

    override fun onAvailable() {
        logText("NetworkCallback onAvailable", false)
        if (wifiHelper.doesSSIDMatch(WIFI_SSID)) {
            proceed()
        } else {
            logText("ssid does not match", true)
        }
    }

    override fun onUnavailable() {
        logText("NetworkCallback onUnavailable", true)
    }

    override fun onLost() {
        logText("NetworkCallback onLost", true)
    }

    override fun onScanResult(result: List<ScanResult>) {
        if (result.isEmpty()) {
            Toast.makeText(this, "Wi-Fi not in range", Toast.LENGTH_LONG).show()
        } else {
            for (sr in result) {
                logText("scanresult ssid: ${sr.SSID}", false)
                if (sr.SSID == WIFI_SSID) {
                    wifiHelper.stopScan()
                    wifiHelper.buildAutoWifiConnection(sr, WIFI_PWD)
                    break
                }
            }
        }
    }

    override fun onPositiveButtonClicked(type: DialogType) {
        when (type) {
            DialogType.LOCATION_PERMISSION_RATIONALE -> requestLocationPermission()
            DialogType.AUTO_WIFI_PERMISSION -> {
                wifiHelper.setAutoWifiPermission(true)
                preCheckConditions()
            }
            DialogType.AUTO_WIFI_RATIONALE -> requestAutoWifiConnectPermission()
            DialogType.WIFI_DISABLED -> preCheckConditions()
            DialogType.LOCATION_WIFI_RATIONALE -> turnOnLocationSettings()
        }
    }

    override fun onNegativeButtonClicked(type: DialogType) {
        when (type) {
            DialogType.AUTO_WIFI_PERMISSION -> {
                wifiHelper.setAutoWifiPermission(false)
                showAutoWifiConnectionRationale()
            }
            else -> taskCancelled()
        }
    }

    override fun onCancelled(type: DialogType) {
        when (type) {
            DialogType.AUTO_WIFI_PERMISSION -> {
                wifiHelper.setAutoWifiPermission(false)
                showAutoWifiConnectionRationale()
            }
            else -> taskCancelled()
        }
    }

    private fun taskCancelled() {
        logText("TASK ABORTED!", true)
    }

    private fun proceed() {
        wifiHelper.setConfigured(true)
        this.onSuccess()
    }

    private fun onSuccess() {
        logText("ALL OK", false)
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