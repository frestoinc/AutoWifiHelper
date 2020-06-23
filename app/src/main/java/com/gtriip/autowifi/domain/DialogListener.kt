package com.gtriip.autowifi.domain

interface DialogListener {

    fun onPositiveButtonClicked(type: DialogType)

    fun onNegativeButtonClicked(type: DialogType)

    fun onCancelled(type: DialogType)
}