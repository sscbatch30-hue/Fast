package com.example.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process state bridge synchronizing SpeedOverlayService telemetry and lifecycle
 * state with MainActivity without requiring heavy AIDL/Binder boilerplate.
 */
object SpeedMeterStateHolder {

  private val _isServiceRunning = MutableStateFlow(false)
  val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

  private val _isPausedScreenOff = MutableStateFlow(false)
  val isPausedScreenOff: StateFlow<Boolean> = _isPausedScreenOff.asStateFlow()

  private val _telemetry = MutableStateFlow(TelemetrySnapshot())
  val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

  private val _serviceAlert = MutableStateFlow<String?>(null)
  val serviceAlert: StateFlow<String?> = _serviceAlert.asStateFlow()

  fun setServiceRunning(running: Boolean) {
    _isServiceRunning.value = running
    if (!running) {
      _isPausedScreenOff.value = false
    }
  }

  fun setPausedScreenOff(paused: Boolean) {
    _isPausedScreenOff.value = paused
  }

  fun updateTelemetry(snapshot: TelemetrySnapshot) {
    _telemetry.value = snapshot
  }

  fun postAlert(message: String?) {
    _serviceAlert.value = message
  }

  fun clearAlert() {
    _serviceAlert.value = null
  }
}
