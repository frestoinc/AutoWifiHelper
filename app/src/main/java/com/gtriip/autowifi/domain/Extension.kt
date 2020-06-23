package com.gtriip.autowifi.domain

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Build


const val REQUEST_CODE_FOR_SWITCH_ON_WIFI = 0x99

const val REQUEST_CODE_FOR_LOCATION_PERMISSION = 0x98

const val REQUEST_CODE_FOR_TURN_ON_LOCATION = 0x97

fun isAndroidQorLater(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}

fun isAndroidPieOrLater(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
}

fun isAndroidOreoOrLater(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}

fun Activity.createDialog(type: DialogType, dialogListener: DialogListener): AlertDialog {
    return AlertDialog.Builder(this)
        .setTitle(type.title())
        .setMessage(type.message())
        .setPositiveButton(
            type.pBtn()
        ) { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
            dialogListener.onPositiveButtonClicked(type)
        }
        .setNegativeButton(
            type.nBtn()
        ) { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
            dialogListener.onNegativeButtonClicked(type)
        }
        .setOnCancelListener { dialog: DialogInterface ->
            dialog.dismiss()
            dialogListener.onCancelled(type)
        }
        .create()
}

fun isHexWepKey(wepKey: String?): Boolean {
    val passwordLen = wepKey?.length ?: 0
    return (passwordLen == 10 || passwordLen == 26 || passwordLen == 58) && wepKey!!.matches(
        Regex("[0-9A-Fa-f]*")
    )
}

fun convertToQuotedString(ssid: String): String {
    if (ssid.isEmpty()) {
        return ""
    }
    val lastPos = ssid.length - 1
    return if (lastPos < 0 || ssid[0] == '"' && ssid[lastPos] == '"') {
        ssid
    } else "\"" + ssid + "\""
}