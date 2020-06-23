package com.gtriip.autowifi.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.gtriip.autowifi.domain.isAndroidOreoOrLater

class HotelLocationHelper(private val appContext: Context) :
    LocationHelper {

    private val packageManager = appContext.packageManager
    private val packageName = appContext.packageName

    companion object {
        val requiredLocationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override val providerChangedIntentAction: String
        get() = LocationManager.PROVIDERS_CHANGED_ACTION

    override val requiredLocationPermissions: Array<String>
        get() = Companion.requiredLocationPermissions

    override fun isLocationEnabled(): Boolean {
        val locationManager =
            appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    override fun locationPermissionsGranted(): Boolean {
        if (isAndroidOreoOrLater()) {
            return requiredLocationPermissions.all { permission ->
                packageManager.checkPermission(
                    permission,
                    packageName
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        return true
    }

}