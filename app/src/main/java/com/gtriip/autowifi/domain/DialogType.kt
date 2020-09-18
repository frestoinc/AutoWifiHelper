package com.gtriip.autowifi.domain

enum class DialogType {

    LOCATION_PERMISSION_RATIONALE {
        override fun title() = "Location Permission"
        override fun message() = "This App needs location permission to read Wi-Fi SSID"
        override fun pBtn() = "Allow"
        override fun nBtn() = "Deny"
    },
    LOCATION_WIFI_RATIONALE {
        override fun title() = "Turn on Location"
        override fun message() =
            "This App need to turn on location to read Wi-Fi SSID (GPS / High Accuracy Option)"

        override fun pBtn() =
            turnOn
        override fun nBtn() = "Dismiss"
    },
    AUTO_WIFI_PERMISSION {
        override fun title() = "Auto-Wifi Connection"
        override fun message() = "Allow this App to auto-connect to Hotel Wi-fi?"
        override fun pBtn() = "Allow"
        override fun nBtn() = "Deny"
    },
    AUTO_WIFI_RATIONALE {
        override fun title() = "Auto-WiFi Denied"
        override fun message() = "This App needs to connect to the Hotel Wi-fi to proceed"
        override fun pBtn() = "Allow"
        override fun nBtn() = "Dismiss"
    },
    WIFI_DISABLED {
        override fun title() = "Wi-Fi is Disabled"
        override fun message() = "This App needs to connect to the Hotel Wi-fi Hotspot"
        override fun pBtn() =
            turnOn
        override fun nBtn() = "Cancel"
    };

    companion object {
        const val turnOn = "Turn On"
    }

    abstract fun title(): String
    abstract fun message(): String
    abstract fun pBtn(): String
    abstract fun nBtn(): String
}

