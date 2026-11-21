package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight SharedPreferences-backed configuration store shared between
 * MainActivity and SpeedOverlayService without heavy serialization overhead.
 */
class MeterPreferences(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _config = MutableStateFlow(loadConfig())
  val config: StateFlow<MeterConfig> = _config.asStateFlow()

  private val listener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _config.value = loadConfig() }

  init {
    prefs.registerOnSharedPreferenceChangeListener(listener)
  }

  fun current(): MeterConfig = _config.value

  fun updateUnit(unit: SpeedUnit) {
    prefs.edit().putString(KEY_UNIT, unit.name).apply()
    _config.value = loadConfig()
  }

  fun updateSize(size: OverlaySize) {
    prefs.edit().putString(KEY_SIZE, size.name).apply()
    _config.value = loadConfig()
  }

  fun updateLayout(layout: OverlayLayout) {
    prefs.edit().putString(KEY_LAYOUT, layout.name).apply()
    _config.value = loadConfig()
  }

  fun updateOpacity(opacity: Float) {
    val clamped = opacity.coerceIn(0.0f, 1.0f)
    prefs.edit().putFloat(KEY_OPACITY, clamped).apply()
    _config.value = loadConfig()
  }

  fun updateRefreshInterval(interval: RefreshInterval) {
    prefs.edit().putString(KEY_REFRESH_INTERVAL, interval.name).apply()
    _config.value = loadConfig()
  }

  fun updateShowGatewayPing(show: Boolean) {
    prefs.edit().putBoolean(KEY_SHOW_PING, show).apply()
    _config.value = loadConfig()
  }

  fun updateProbeTendaRouter(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_PROBE_ROUTER, enabled).apply()
    _config.value = loadConfig()
  }

  fun updateRouterCredentials(routerIp: String, password: String) {
    val sanitizedIp = routerIp.trim().ifEmpty { MeterConfig.DEFAULT_ROUTER_IP }
    prefs
      .edit()
      .putString(KEY_ROUTER_IP, sanitizedIp)
      .putString(KEY_ROUTER_PASSWORD, password)
      .apply()
    _config.value = loadConfig()
  }

  fun saveOverlayPosition(x: Int, y: Int) {
    prefs.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
    _config.value = loadConfig()
  }

  fun resetOverlayPosition() {
    prefs
      .edit()
      .putInt(KEY_OVERLAY_X, MeterConfig.DEFAULT_OVERLAY_X)
      .putInt(KEY_OVERLAY_Y, MeterConfig.DEFAULT_OVERLAY_Y)
      .apply()
    _config.value = loadConfig()
  }

  private fun loadConfig(): MeterConfig {
    val unitName = prefs.getString(KEY_UNIT, SpeedUnit.AUTO_BYTES.name)
    val sizeName = prefs.getString(KEY_SIZE, OverlaySize.STANDARD.name)
    val layoutName = prefs.getString(KEY_LAYOUT, OverlayLayout.STACKED.name)
    val intervalName = prefs.getString(KEY_REFRESH_INTERVAL, RefreshInterval.FAST_1S.name)

    return MeterConfig(
      unit = enumValueOrDefault(unitName, SpeedUnit.AUTO_BYTES),
      size = enumValueOrDefault(sizeName, OverlaySize.STANDARD),
      layout = enumValueOrDefault(layoutName, OverlayLayout.STACKED),
      opacity = prefs.getFloat(KEY_OPACITY, 0.88f).coerceIn(0.0f, 1.0f),
      refreshInterval = enumValueOrDefault(intervalName, RefreshInterval.FAST_1S),
      showGatewayPing = prefs.getBoolean(KEY_SHOW_PING, false),
      probeTendaRouter = prefs.getBoolean(KEY_PROBE_ROUTER, true),
      routerIp = prefs.getString(KEY_ROUTER_IP, MeterConfig.DEFAULT_ROUTER_IP)
        ?: MeterConfig.DEFAULT_ROUTER_IP,
      routerPassword = prefs.getString(KEY_ROUTER_PASSWORD, "") ?: "",
      overlayX = prefs.getInt(KEY_OVERLAY_X, MeterConfig.DEFAULT_OVERLAY_X),
      overlayY = prefs.getInt(KEY_OVERLAY_Y, MeterConfig.DEFAULT_OVERLAY_Y),
    )
  }

  private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, fallback: T): T {
    if (name == null) return fallback
    return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
  }

  companion object {
    private const val PREFS_NAME = "tenda_f3_speed_meter_prefs"
    private const val KEY_UNIT = "speed_unit"
    private const val KEY_SIZE = "overlay_size"
    private const val KEY_LAYOUT = "overlay_layout"
    private const val KEY_OPACITY = "overlay_opacity"
    private const val KEY_REFRESH_INTERVAL = "refresh_interval"
    private const val KEY_SHOW_PING = "show_gateway_ping"
    private const val KEY_PROBE_ROUTER = "probe_tenda_router"
    private const val KEY_ROUTER_IP = "router_ip"
    private const val KEY_ROUTER_PASSWORD = "router_password"
    private const val KEY_OVERLAY_X = "overlay_x"
    private const val KEY_OVERLAY_Y = "overlay_y"
  }
}
