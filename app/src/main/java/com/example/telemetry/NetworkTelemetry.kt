package com.example.telemetry

enum class TransportType(val displayName: String) {
  WIFI("Wi-Fi"),
  CELLULAR("Mobile Data"),
  ETHERNET("Ethernet"),
  VPN("VPN"),
  NONE("Offline"),
}

data class SpeedSample(
  val downloadBytesPerSec: Long,
  val uploadBytesPerSec: Long,
  val timestampMs: Long,
)

data class TelemetrySnapshot(
  val downloadBytesPerSec: Long = 0L,
  val uploadBytesPerSec: Long = 0L,
  val peakDownloadBytesPerSec: Long = 0L,
  val peakUploadBytesPerSec: Long = 0L,
  val sessionDownloadBytes: Long = 0L,
  val sessionUploadBytes: Long = 0L,
  val transportType: TransportType = TransportType.NONE,
  val isInternetValidated: Boolean = false,
  val detectedGatewayIp: String? = null,
  val wifiLinkSpeedMbps: Int? = null,
  val wifiSignalDbm: Int? = null,
  val gatewayPingMs: Int? = null,
  val isTendaRouterDetected: Boolean = false,
  val routerStatusLabel: String = "Device TrafficStats",
  val history: List<SpeedSample> = emptyList(),
)
