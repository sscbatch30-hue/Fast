package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MeterPreferences
import com.example.data.OverlayLayout
import com.example.data.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TendaF3SpeedMeter", appName)
  }

  @Test
  fun `preferences persist overlay config`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = MeterPreferences(context)
    prefs.updateUnit(SpeedUnit.BITS_PER_SEC)
    prefs.updateLayout(OverlayLayout.INLINE)
    prefs.updateOpacity(0.0f)
    prefs.saveOverlayPosition(120, 260)

    val loaded = prefs.current()
    assertEquals(SpeedUnit.BITS_PER_SEC, loaded.unit)
    assertEquals(OverlayLayout.INLINE, loaded.layout)
    assertEquals(0.0f, loaded.opacity, 0.001f)
    assertEquals(120, loaded.overlayX)
    assertEquals(260, loaded.overlayY)

    prefs.updateOpacity(0.55f)
    assertEquals(0.55f, prefs.current().opacity, 0.001f)
  }
}
