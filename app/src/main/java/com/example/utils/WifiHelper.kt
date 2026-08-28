package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object WifiHelper {

    fun connectToWifi(context: Context, ssid: String, password: String?, encryption: String? = "WPA"): Boolean {
        // Copy password to clipboard first so user has it immediately
        if (!password.isNullOrEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Wi-Fi Password", password)
            clipboard?.setPrimaryClip(clip)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+, we can open WiFi settings or launch direct panel
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (panelIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(panelIntent)
                    Toast.makeText(
                        context,
                        "Password copied to clipboard. Select '$ssid' to connect.",
                        Toast.LENGTH_LONG
                    ).show()
                    return true
                }
            }

            // Fallback to standard Wi-Fi settings
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Password copied. Select '$ssid' in Wi-Fi settings.",
                Toast.LENGTH_LONG
            ).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open Wi-Fi settings: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }
}
