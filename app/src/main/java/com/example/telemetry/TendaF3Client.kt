package com.example.telemetry

import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class TendaProbeResult(
  val reachable: Boolean,
  val pingMs: Int?,
  val isTendaF3Detected: Boolean,
  val wanDownloadBytesPerSec: Long? = null,
  val wanUploadBytesPerSec: Long? = null,
  val statusMessage: String,
)

/**
 * Lightweight local LAN client for measuring gateway TCP latency and optionally
 * polling Tenda F3 router WAN throughput status when connected to the router LAN.
 */
class TendaF3Client {

  @Volatile private var sessionCookie: String? = null

  suspend fun probeRouter(routerIp: String, password: String): TendaProbeResult =
    withContext(Dispatchers.IO) {
      val host = routerIp.trim()
      if (!isValidHost(host)) {
        return@withContext TendaProbeResult(
          reachable = false,
          pingMs = null,
          isTendaF3Detected = false,
          statusMessage = "Invalid router IP",
        )
      }

      val pingMs = measureTcpLatencyMs(host, 80, timeoutMs = 900)
      if (pingMs == null) {
        return@withContext TendaProbeResult(
          reachable = false,
          pingMs = null,
          isTendaF3Detected = false,
          statusMessage = "Gateway $host unreachable · Using Device TrafficStats",
        )
      }

      // Attempt lightweight Tenda F3 status endpoint query
      val statusJson = fetchTendaStatusJson(host, password)
      if (statusJson != null) {
        val speeds = parseWanSpeedsBytesPerSec(statusJson)
        return@withContext TendaProbeResult(
          reachable = true,
          pingMs = pingMs,
          isTendaF3Detected = true,
          wanDownloadBytesPerSec = speeds?.first,
          wanUploadBytesPerSec = speeds?.second,
          statusMessage = "Tenda F3 Online · ${pingMs}ms",
        )
      }

      TendaProbeResult(
        reachable = true,
        pingMs = pingMs,
        isTendaF3Detected = false,
        statusMessage = "Gateway $host (${pingMs}ms) · Device TrafficStats",
      )
    }

  private fun measureTcpLatencyMs(host: String, port: Int, timeoutMs: Int): Int? {
    return runCatching {
      val start = SystemClock.elapsedRealtime()
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMs)
      }
      (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
    }.getOrNull()
  }

  private fun fetchTendaStatusJson(host: String, password: String): JSONObject? {
    val endpoints = listOf(
      "http://$host/goform/getStatus?random=${System.currentTimeMillis()}",
      "http://$host/goform/GetSysStatus?random=${System.currentTimeMillis()}",
    )

    for (endpoint in endpoints) {
      val body = httpGet(endpoint)
      val json = body?.let { parseJsonSafe(it) }
      if (json != null && looksLikeTendaStatus(json)) {
        return json
      }
    }

    // If router requires authentication and password is provided, attempt login once
    if (password.isNotEmpty() && sessionCookie == null) {
      if (authenticateTenda(host, password)) {
        for (endpoint in endpoints) {
          val body = httpGet(endpoint)
          val json = body?.let { parseJsonSafe(it) }
          if (json != null && looksLikeTendaStatus(json)) {
            return json
          }
        }
      }
    }

    return null
  }

  private fun authenticateTenda(host: String, password: String): Boolean {
    return runCatching {
      val url = URL("http://$host/login/Auth")
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 1000
        readTimeout = 1000
        instanceFollowRedirects = false
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      }
      try {
        val hashed = md5Hex(password)
        val payload = "username=admin&password=${URLEncoder.encode(hashed, "UTF-8")}"
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val cookie = conn.headerFields["Set-Cookie"]?.firstOrNull()?.substringBefore(";")
        if (!cookie.isNullOrBlank()) {
          sessionCookie = cookie
          true
        } else {
          false
        }
      } finally {
        conn.disconnect()
      }
    }.getOrDefault(false)
  }

  private fun httpGet(urlString: String): String? {
    return runCatching {
      val url = URL(urlString)
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 1000
        readTimeout = 1000
        sessionCookie?.let { setRequestProperty("Cookie", it) }
      }
      try {
        if (conn.responseCode in 200..299) {
          BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
              sb.append(line)
              if (sb.length > 32_768) break
            }
            sb.toString()
          }
        } else {
          if (conn.responseCode == 401 || conn.responseCode == 403) {
            sessionCookie = null
          }
          null
        }
      } finally {
        conn.disconnect()
      }
    }.getOrNull()
  }

  private fun parseJsonSafe(raw: String): JSONObject? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return null
    return runCatching { JSONObject(trimmed) }.getOrNull()
  }

  private fun looksLikeTendaStatus(json: JSONObject): Boolean {
    return json.has("statusDownSpeed") ||
      json.has("statusUpSpeed") ||
      json.has("wanConnectStatus") ||
      json.has("internetStatus") ||
      json.has("wanStatus")
  }

  private fun parseWanSpeedsBytesPerSec(json: JSONObject): Pair<Long, Long>? {
    // Tenda F3 firmware commonly reports KB/s strings/numbers in statusDownSpeed / statusUpSpeed
    val downKb = json.optString("statusDownSpeed", "").toDoubleOrNull()
    val upKb = json.optString("statusUpSpeed", "").toDoubleOrNull()
    if (downKb != null && upKb != null) {
      return (downKb * 1024.0).toLong().coerceAtLeast(0L) to
        (upKb * 1024.0).toLong().coerceAtLeast(0L)
    }
    return null
  }

  private fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun isValidHost(host: String): Boolean {
    if (host.isEmpty() || host.length > 64) return false
    return IPV4_REGEX.matches(host) || HOSTNAME_REGEX.matches(host)
  }

  companion object {
    private val IPV4_REGEX =
      Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
    private val HOSTNAME_REGEX = Regex("^[a-zA-Z0-9.-]+$")
  }
}
