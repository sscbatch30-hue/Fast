package com.example.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import java.net.Inet4Address

data class RawNetworkInfo(
  val transportType: TransportType,
  val isInternetValidated: Boolean,
  val detectedGatewayIp: String?,
  val wifiLinkSpeedMbps: Int?,
  val wifiSignalDbm: Int?,
)

/**
 * Lightweight sampler reading OS TrafficStats deltas and ConnectivityManager state
 * with zero blocking network I/O and minimal CPU wakeups.
 */
class SpeedSampler(context: Context) {

  private val appContext = context.applicationContext
  private val connectivityManager =
    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private val wifiManager =
    appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

  private var lastRxBytes: Long = TrafficStats.UNSUPPORTED.toLong()
  private var lastTxBytes: Long = TrafficStats.UNSUPPORTED.toLong()
  private var lastSampleTimeMs: Long = 0L

  fun resetBaseline() {
    lastRxBytes = safeTotalRxBytes()
    lastTxBytes = safeTotalTxBytes()
    lastSampleTimeMs = SystemClock.elapsedRealtime()
  }

  /**
   * Computes download and upload rates in bytes/sec since the last sample,
   * returning Pair(downloadBytesPerSec, uploadBytesPerSec) and delta bytes Pair(deltaRx, deltaTx).
   */
  fun sampleTrafficDelta(): TrafficDelta {
    val nowMs = SystemClock.elapsedRealtime()
    val currentRx = safeTotalRxBytes()
    val currentTx = safeTotalTxBytes()

    if (lastSampleTimeMs == 0L || lastRxBytes < 0L || lastTxBytes < 0L || currentRx < 0L || currentTx < 0L) {
      lastRxBytes = currentRx
      lastTxBytes = currentTx
      lastSampleTimeMs = nowMs
      return TrafficDelta(0L, 0L, 0L, 0L)
    }

    val elapsedMs = (nowMs - lastSampleTimeMs).coerceAtLeast(1L)
    val deltaRx = (currentRx - lastRxBytes).coerceAtLeast(0L)
    val deltaTx = (currentTx - lastTxBytes).coerceAtLeast(0L)

    lastRxBytes = currentRx
    lastTxBytes = currentTx
    lastSampleTimeMs = nowMs

    val rxPerSec = (deltaRx * 1000L) / elapsedMs
    val txPerSec = (deltaTx * 1000L) / elapsedMs
    return TrafficDelta(
      downloadBytesPerSec = rxPerSec,
      uploadBytesPerSec = txPerSec,
      deltaRxBytes = deltaRx,
      deltaTxBytes = deltaTx,
    )
  }

  fun readNetworkInfo(): RawNetworkInfo {
    val activeNetwork = connectivityManager.activeNetwork
    val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
    val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }

    val transport =
      when {
        capabilities == null -> TransportType.NONE
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportType.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportType.CELLULAR
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportType.ETHERNET
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> TransportType.VPN
        else -> TransportType.NONE
      }

    val validated =
      capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    val gatewayIp =
      linkProperties
        ?.routes
        ?.firstOrNull { route ->
          route.destination.prefixLength == 0 && route.gateway is Inet4Address
        }
        ?.gateway
        ?.hostAddress

    var linkSpeedMbps: Int? = null
    var signalDbm: Int? = null

    if (transport == TransportType.WIFI) {
      val wifiInfo: WifiInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          capabilities?.transportInfo as? WifiInfo
        } else {
          @Suppress("DEPRECATION") wifiManager.connectionInfo
        }

      if (wifiInfo != null) {
        if (wifiInfo.linkSpeed > 0) linkSpeedMbps = wifiInfo.linkSpeed
        if (wifiInfo.rssi in -120..-1) signalDbm = wifiInfo.rssi
      }

      if (signalDbm == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && capabilities != null) {
        val sig = capabilities.signalStrength
        if (sig != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && sig in -120..-1) {
          signalDbm = sig
        }
      }
    }

    return RawNetworkInfo(
      transportType = transport,
      isInternetValidated = validated,
      detectedGatewayIp = gatewayIp,
      wifiLinkSpeedMbps = linkSpeedMbps,
      wifiSignalDbm = signalDbm,
    )
  }

  private fun safeTotalRxBytes(): Long {
    val v = TrafficStats.getTotalRxBytes()
    return if (v == TrafficStats.UNSUPPORTED.toLong()) -1L else v
  }

  private fun safeTotalTxBytes(): Long {
    val v = TrafficStats.getTotalTxBytes()
    return if (v == TrafficStats.UNSUPPORTED.toLong()) -1L else v
  }
}

data class TrafficDelta(
  val downloadBytesPerSec: Long,
  val uploadBytesPerSec: Long,
  val deltaRxBytes: Long,
  val deltaTxBytes: Long,
)
