package com.example.telemetry

import com.example.data.SpeedUnit
import java.util.Locale

object SpeedFormatter {

  fun formatSpeed(bytesPerSec: Long, unit: SpeedUnit): String {
    val safeBytes = bytesPerSec.coerceAtLeast(0L)
    return when (unit) {
      SpeedUnit.AUTO_BYTES -> formatBytesPerSecond(safeBytes)
      SpeedUnit.BITS_PER_SEC -> formatBitsPerSecond(safeBytes * 8L)
    }
  }

  private fun formatBytesPerSecond(bytesPerSec: Long): String {
    return when {
      bytesPerSec < 1024L -> String.format(Locale.US, "%d B/s", bytesPerSec)
      bytesPerSec < 1024L * 1024L -> {
        val kb = bytesPerSec / 1024.0
        if (kb >= 100.0) String.format(Locale.US, "%.0f KB/s", kb)
        else String.format(Locale.US, "%.1f KB/s", kb)
      }
      bytesPerSec < 1024L * 1024L * 1024L -> {
        val mb = bytesPerSec / (1024.0 * 1024.0)
        String.format(Locale.US, "%.2f MB/s", mb)
      }
      else -> {
        val gb = bytesPerSec / (1024.0 * 1024.0 * 1024.0)
        String.format(Locale.US, "%.2f GB/s", gb)
      }
    }
  }

  private fun formatBitsPerSecond(bitsPerSec: Long): String {
    return when {
      bitsPerSec < 1000L -> String.format(Locale.US, "%d bps", bitsPerSec)
      bitsPerSec < 1_000_000L -> {
        val kbps = bitsPerSec / 1000.0
        if (kbps >= 100.0) String.format(Locale.US, "%.0f Kbps", kbps)
        else String.format(Locale.US, "%.1f Kbps", kbps)
      }
      else -> {
        val mbps = bitsPerSec / 1_000_000.0
        String.format(Locale.US, "%.2f Mbps", mbps)
      }
    }
  }

  fun formatDataTotal(totalBytes: Long): String {
    val safe = totalBytes.coerceAtLeast(0L)
    return when {
      safe < 1024L -> "$safe B"
      safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
      safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
      else -> String.format(Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
    }
  }
}
