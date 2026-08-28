package com.example.utils

import com.example.data.model.QrType
import com.example.data.model.ScanHistoryItem
import java.util.regex.Pattern

data class ParsedQrResult(
    val rawValue: String,
    val type: QrType,
    val title: String,
    val displayDetails: String,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val wifiEncryption: String? = null
) {
    fun toScanHistoryItem(): ScanHistoryItem {
        return ScanHistoryItem(
            rawValue = rawValue,
            type = type,
            title = title,
            displayDetails = displayDetails,
            wifiSsid = wifiSsid,
            wifiPassword = wifiPassword,
            wifiEncryption = wifiEncryption,
            timestamp = System.currentTimeMillis()
        )
    }
}

object QrCodeParser {

    fun parse(raw: String): ParsedQrResult {
        val trimmed = raw.trim()

        // 1. Wi-Fi: WIFI:S:MySSID;T:WPA;P:MyPassword;; or WIFI:T:WPA;S:MySSID;P:MyPassword;;
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val ssid = extractWifiField(trimmed, "S")
            val password = extractWifiField(trimmed, "P")
            val type = extractWifiField(trimmed, "T") ?: "WPA"
            val hidden = extractWifiField(trimmed, "H") ?: "false"

            val title = if (!ssid.isNullOrEmpty()) "Wi-Fi: $ssid" else "Wi-Fi Network"
            val details = buildString {
                append("SSID: ").append(ssid ?: "Hidden").append("\n")
                if (!password.isNullOrEmpty()) {
                    append("Password: ").append(password).append("\n")
                }
                append("Security: ").append(type)
            }

            return ParsedQrResult(
                rawValue = trimmed,
                type = QrType.WIFI,
                title = title,
                displayDetails = details,
                wifiSsid = ssid,
                wifiPassword = password,
                wifiEncryption = type
            )
        }

        // 2. URL
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            (trimmed.contains(".") && !trimmed.contains(" ") && (trimmed.startsWith("www.", ignoreCase = true) || trimmed.matches(Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))))
        ) {
            val url = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                "https://$trimmed"
            } else trimmed

            val domain = try {
                val clean = url.substringAfter("://").substringBefore("/")
                clean.removePrefix("www.")
            } catch (e: Exception) {
                "Web Link"
            }

            return ParsedQrResult(
                rawValue = url,
                type = QrType.URL,
                title = "Link: $domain",
                displayDetails = url
            )
        }

        // 3. Email: mailto:test@example.com?subject=Hello
        if (trimmed.startsWith("mailto:", ignoreCase = true) || trimmed.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"))) {
            val email = trimmed.removePrefix("mailto:").substringBefore("?")
            return ParsedQrResult(
                rawValue = trimmed,
                type = QrType.EMAIL,
                title = "Email Address",
                displayDetails = email
            )
        }

        // 4. Phone: tel:+123456789
        if (trimmed.startsWith("tel:", ignoreCase = true) || (trimmed.startsWith("+") && trimmed.length > 5 && trimmed.substring(1).all { it.isDigit() || it == '-' || it == ' ' })) {
            val phone = trimmed.removePrefix("tel:")
            return ParsedQrResult(
                rawValue = trimmed,
                type = QrType.PHONE,
                title = "Phone Number",
                displayDetails = phone
            )
        }

        // 5. SMS: smsto:12345:message or sms:12345
        if (trimmed.startsWith("smsto:", ignoreCase = true) || trimmed.startsWith("sms:", ignoreCase = true)) {
            val body = trimmed.substringAfter(":")
            return ParsedQrResult(
                rawValue = trimmed,
                type = QrType.SMS,
                title = "SMS Message",
                displayDetails = body
            )
        }

        // 6. Geo: geo:37.7749,-122.4194
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            val coords = trimmed.removePrefix("geo:").substringBefore("?")
            return ParsedQrResult(
                rawValue = trimmed,
                type = QrType.GEO,
                title = "Location Coordinates",
                displayDetails = coords
            )
        }

        // 7. Plain Text
        val firstLine = trimmed.lineSequence().firstOrNull() ?: trimmed
        val title = if (firstLine.length > 35) firstLine.take(32) + "..." else firstLine
        return ParsedQrResult(
            rawValue = trimmed,
            type = QrType.TEXT,
            title = if (title.isNotBlank()) title else "Plain Text",
            displayDetails = trimmed
        )
    }

    private fun extractWifiField(qr: String, key: String): String? {
        val payload = if (qr.startsWith("WIFI:", ignoreCase = true)) qr.substring(5) else qr
        val pattern = Pattern.compile("(?:^|;)$key:([^;]*)(?:;|$)")
        val matcher = pattern.matcher(payload)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
}
