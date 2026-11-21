package com.example

import com.example.data.SpeedUnit
import com.example.telemetry.SpeedFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun formatSpeed_autoBytes() {
    assertEquals("0 B/s", SpeedFormatter.formatSpeed(0L, SpeedUnit.AUTO_BYTES))
    assertEquals("512 B/s", SpeedFormatter.formatSpeed(512L, SpeedUnit.AUTO_BYTES))
    assertEquals("1.5 KB/s", SpeedFormatter.formatSpeed(1536L, SpeedUnit.AUTO_BYTES))
    assertEquals("2.00 MB/s", SpeedFormatter.formatSpeed(2L * 1024L * 1024L, SpeedUnit.AUTO_BYTES))
  }

  @Test
  fun formatSpeed_bitsPerSec() {
    assertEquals("0 bps", SpeedFormatter.formatSpeed(0L, SpeedUnit.BITS_PER_SEC))
    assertEquals("8.0 Kbps", SpeedFormatter.formatSpeed(1000L, SpeedUnit.BITS_PER_SEC))
    assertEquals("8.00 Mbps", SpeedFormatter.formatSpeed(1_000_000L, SpeedUnit.BITS_PER_SEC))
  }

  @Test
  fun formatDataTotal_scalesCleanly() {
    assertEquals("500 B", SpeedFormatter.formatDataTotal(500L))
    assertEquals("2.0 KB", SpeedFormatter.formatDataTotal(2048L))
    assertEquals("1.5 MB", SpeedFormatter.formatDataTotal((1.5 * 1024 * 1024).toLong()))
  }
}
