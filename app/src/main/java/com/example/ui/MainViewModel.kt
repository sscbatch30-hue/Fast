package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MeterConfig
import com.example.data.MeterPreferences
import com.example.data.OverlayLayout
import com.example.data.OverlaySize
import com.example.data.RefreshInterval
import com.example.data.SpeedUnit
import com.example.service.SpeedOverlayService
import com.example.telemetry.SpeedMeterStateHolder
import com.example.telemetry.SpeedSample
import com.example.telemetry.SpeedSampler
import com.example.telemetry.TelemetrySnapshot
import com.example.telemetry.TendaF3Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
  val isServiceRunning: Boolean = false,
  val isPausedScreenOff: Boolean = false,
  val hasOverlayPermission: Boolean = false,
  val hasNotificationPermission: Boolean = true,
  val config: MeterConfig = MeterConfig(),
  val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
  val serviceAlert: String? = null,
  val isTestingRouter: Boolean = false,
  val routerTestResult: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application
  private val preferences = MeterPreferences(application)
  private val previewSampler = SpeedSampler(application)
  private val tendaClient = TendaF3Client()

  private val hasOverlayPermission = MutableStateFlow(checkOverlayPermission())
  private val hasNotificationPermission = MutableStateFlow(checkNotificationPermission())
  private val previewTelemetry = MutableStateFlow(TelemetrySnapshot())
  private val isTestingRouter = MutableStateFlow(false)
  private val routerTestResult = MutableStateFlow<String?>(null)

  private var previewJob: Job? = null
  private val previewHistory = ArrayDeque<SpeedSample>(30)
  private var previewPeakDl = 0L
  private var previewPeakUl = 0L
  private var previewSessionDl = 0L
  private var previewSessionUl = 0L

  val uiState: StateFlow<MainUiState> =
    combine(
        SpeedMeterStateHolder.isServiceRunning,
        SpeedMeterStateHolder.isPausedScreenOff,
        hasOverlayPermission,
        hasNotificationPermission,
        preferences.config,
        SpeedMeterStateHolder.telemetry,
        previewTelemetry,
        SpeedMeterStateHolder.serviceAlert,
        isTestingRouter,
        routerTestResult,
      ) { values ->
        val running = values[0] as Boolean
        val paused = values[1] as Boolean
        val overlayPerm = values[2] as Boolean
        val notifPerm = values[3] as Boolean
        val config = values[4] as MeterConfig
        val serviceTele = values[5] as TelemetrySnapshot
        val previewTele = values[6] as TelemetrySnapshot
        val alert = values[7] as String?
        val testing = values[8] as Boolean
        val testResult = values[9] as String?

        MainUiState(
          isServiceRunning = running,
          isPausedScreenOff = paused,
          hasOverlayPermission = overlayPerm,
          hasNotificationPermission = notifPerm,
          config = config,
          telemetry = if (running) serviceTele else previewTele,
          serviceAlert = alert,
          isTestingRouter = testing,
          routerTestResult = testResult,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue =
          MainUiState(
            hasOverlayPermission = checkOverlayPermission(),
            hasNotificationPermission = checkNotificationPermission(),
            config = preferences.current(),
          ),
      )

  init {
    observeServiceForPreview()
  }

  fun refreshPermissions() {
    hasOverlayPermission.value = checkOverlayPermission()
    hasNotificationPermission.value = checkNotificationPermission()
  }

  /**
   * Starts the floating speed overlay foreground service if overlay permission is granted.
   * Returns false if the user still needs to grant Display Over Other Apps permission.
   */
  fun startOverlayService(): Boolean {
    refreshPermissions()
    if (!hasOverlayPermission.value) {
      return false
    }
    SpeedMeterStateHolder.clearAlert()
    ContextCompat.startForegroundService(app, SpeedOverlayService.createStartIntent(app))
    return true
  }

  fun stopOverlayService() {
    if (SpeedMeterStateHolder.isServiceRunning.value) {
      app.startService(SpeedOverlayService.createStopIntent(app))
    }
  }

  fun resetOverlayPosition() {
    preferences.resetOverlayPosition()
    if (SpeedMeterStateHolder.isServiceRunning.value) {
      app.startService(SpeedOverlayService.createResetPositionIntent(app))
    }
  }

  fun dismissAlert() {
    SpeedMeterStateHolder.clearAlert()
  }

  fun updateSpeedUnit(unit: SpeedUnit) = preferences.updateUnit(unit)

  fun updateOverlaySize(size: OverlaySize) = preferences.updateSize(size)

  fun updateOverlayLayout(layout: OverlayLayout) = preferences.updateLayout(layout)

  fun updateOpacity(opacity: Float) = preferences.updateOpacity(opacity)

  fun updateRefreshInterval(interval: RefreshInterval) = preferences.updateRefreshInterval(interval)

  fun updateShowGatewayPing(show: Boolean) = preferences.updateShowGatewayPing(show)

  fun updateProbeTendaRouter(enabled: Boolean) {
    preferences.updateProbeTendaRouter(enabled)
    routerTestResult.value = null
  }

  fun updateRouterCredentials(ip: String, password: String) {
    preferences.updateRouterCredentials(ip, password)
  }

  fun detectAndApplyGatewayIp(): String? {
    val detected = uiState.value.telemetry.detectedGatewayIp
    if (!detected.isNullOrBlank()) {
      preferences.updateRouterCredentials(detected, preferences.current().routerPassword)
    }
    return detected
  }

  fun testRouterConnection(routerIp: String, password: String) {
    preferences.updateRouterCredentials(routerIp, password)
    viewModelScope.launch {
      isTestingRouter.value = true
      routerTestResult.value = null
      val result = tendaClient.probeRouter(routerIp, password)
      isTestingRouter.value = false
      routerTestResult.value = result.statusMessage
    }
  }

  private fun observeServiceForPreview() {
    viewModelScope.launch {
      SpeedMeterStateHolder.isServiceRunning.collect { running ->
        if (running) {
          previewJob?.cancel()
          previewJob = null
        } else {
          startPreviewPolling()
        }
      }
    }
  }

  private fun startPreviewPolling() {
    if (previewJob?.isActive == true) return
    previewSampler.resetBaseline()
    previewJob =
      viewModelScope.launch {
        while (isActive && !SpeedMeterStateHolder.isServiceRunning.value) {
          val cfg = preferences.current()
          val snapshot =
            withContext(Dispatchers.Default) {
              val delta = previewSampler.sampleTrafficDelta()
              val info = previewSampler.readNetworkInfo()

              previewSessionDl += delta.deltaRxBytes
              previewSessionUl += delta.deltaTxBytes
              if (delta.downloadBytesPerSec > previewPeakDl) previewPeakDl = delta.downloadBytesPerSec
              if (delta.uploadBytesPerSec > previewPeakUl) previewPeakUl = delta.uploadBytesPerSec

              val sample =
                SpeedSample(
                  downloadBytesPerSec = delta.downloadBytesPerSec,
                  uploadBytesPerSec = delta.uploadBytesPerSec,
                  timestampMs = SystemClock.elapsedRealtime(),
                )
              if (previewHistory.size >= 30) previewHistory.removeFirst()
              previewHistory.addLast(sample)

              TelemetrySnapshot(
                downloadBytesPerSec = delta.downloadBytesPerSec,
                uploadBytesPerSec = delta.uploadBytesPerSec,
                peakDownloadBytesPerSec = previewPeakDl,
                peakUploadBytesPerSec = previewPeakUl,
                sessionDownloadBytes = previewSessionDl,
                sessionUploadBytes = previewSessionUl,
                transportType = info.transportType,
                isInternetValidated = info.isInternetValidated,
                detectedGatewayIp = info.detectedGatewayIp,
                wifiLinkSpeedMbps = info.wifiLinkSpeedMbps,
                wifiSignalDbm = info.wifiSignalDbm,
                routerStatusLabel = "Device TrafficStats (${info.transportType.displayName})",
                history = previewHistory.toList(),
              )
            }
          previewTelemetry.value = snapshot
          delay(cfg.refreshInterval.millis)
        }
      }
  }

  private fun checkOverlayPermission(): Boolean = Settings.canDrawOverlays(app)

  private fun checkNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }
}
