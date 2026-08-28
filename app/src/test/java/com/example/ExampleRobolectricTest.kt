package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.QrType
import com.example.utils.QrCodeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SnapScan AI", appName)
  }

  @Test
  fun `qr parser correctly parses wifi network`() {
    val rawWifi = "WIFI:S:MyHomeWifi;T:WPA;P:SuperSecretPass;;"
    val result = QrCodeParser.parse(rawWifi)

    assertEquals(QrType.WIFI, result.type)
    assertEquals("MyHomeWifi", result.wifiSsid)
    assertEquals("SuperSecretPass", result.wifiPassword)
    assertEquals("WPA", result.wifiEncryption)
  }

  @Test
  fun `qr parser correctly parses url`() {
    val rawUrl = "https://ai.google.dev"
    val result = QrCodeParser.parse(rawUrl)

    assertEquals(QrType.URL, result.type)
    assertEquals("https://ai.google.dev", result.rawValue)
  }

  @Test
  fun `qr parser correctly parses plain text`() {
    val rawText = "Hello SnapScan AI world!"
    val result = QrCodeParser.parse(rawText)

    assertEquals(QrType.TEXT, result.type)
    assertEquals("Hello SnapScan AI world!", result.rawValue)
  }
}
