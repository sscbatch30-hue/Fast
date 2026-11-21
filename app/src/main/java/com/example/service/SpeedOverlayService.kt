package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.example.MainActivity
import com.example.R
import com.example.data.MeterConfig
import com.example.data.MeterPreferences
import com.example.data.OverlayLayout
import com.example.telemetry.SpeedFormatter
import com.example.telemetry.SpeedMeterStateHolder
import com.example.telemetry.SpeedSample
import com.example.telemetry.SpeedSampler
import com.example.telemetry.TelemetrySnapshot
import com.example.telemetry.TendaF3Client
import com.example.telemetry.TendaProbeResult
import com.example.telemetry.TransportType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service rendering a lightweight, draggable floating speed meter pill
 * over other applications without intercepting touches outside the widget.
 *
 * Battery optimization: Automatically pauses polling when the device screen turns off
 * and resumes cleanly when the screen wakes up.
 */
class SpeedOverlayService : Service() {

  private lateinit var windowManager: WindowManager
  private lateinit var notificationManager: NotificationManager
  private lateinit var preferences: MeterPreferences
  private lateinit var speedSampler: SpeedSampler
  private val tendaClient = TendaF3Client()

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val isScreenInteractive = MutableStateFlow(true)

  private var pollingJob: Job? = null
  private var configObserverJob: Job? = null
  private var isStopping = false

  // Overlay View references
  private var overlayContainer: LinearLayout? = null
  private var downloadTextView: TextView? = null
  private var uploadTextView: TextView? = null
  private var pingTextView: TextView? = null
  private var overlayParams: WindowManager.LayoutParams? = null
  private var overlayBackground: GradientDrawable? = null
  private var monoTypeface: Typeface? = null

  // Session telemetry accumulators
  private var peakDownloadBps = 0L
  private var peakUploadBps = 0L
  private var sessionDownloadBytes = 0L
  private var sessionUploadBytes = 0L
  private val sampleHistory = ArrayDeque<SpeedSample>(MAX_HISTORY_SAMPLES)
  private var lastTendaProbe: TendaProbeResult? = null
  private var tickCounter = 0

  private val screenStateReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          Intent.ACTION_SCREEN_OFF -> {
            isScreenInteractive.value = false
            SpeedMeterStateHolder.setPausedScreenOff(true)
          }
          Intent.ACTION_SCREEN_ON -> {
            speedSampler.resetBaseline()
            isScreenInteractive.value = true
            SpeedMeterStateHolder.setPausedScreenOff(false)
          }
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    preferences = MeterPreferences(this)
    speedSampler = SpeedSampler(this)
    monoTypeface = ResourcesCompat.getFont(this, R.font.jetbrains_mono)

    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    isScreenInteractive.value = powerManager.isInteractive

    createNotificationChannel()
    registerScreenReceiver()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopServiceCleanly()
        return START_NOT_STICKY
      }
      ACTION_RESET_POSITION -> {
        preferences.resetOverlayPosition()
        applySavedPosition()
        return START_STICKY
      }
      ACTION_REFRESH_CONFIG -> {
        applyOverlayAppearance(preferences.current())
        return START_STICKY
      }
    }

    // Must promote to foreground promptly on Android 8+ before returning
    promoteToForeground(buildNotification("↓ 0 B/s  ↑ 0 B/s"))

    if (!Settings.canDrawOverlays(this)) {
      SpeedMeterStateHolder.postAlert(getString(R.string.error_overlay_permission_required))
      stopServiceCleanly()
      return START_NOT_STICKY
    }

    if (overlayContainer == null) {
      attachOverlayWindow()
    }

    SpeedMeterStateHolder.setServiceRunning(true)
    SpeedMeterStateHolder.clearAlert()
    startConfigObserverIfNeeded()
    startPollingLoopIfNeeded()

    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    isStopping = true
    pollingJob?.cancel()
    configObserverJob?.cancel()
    serviceScope.cancel()
    unregisterScreenReceiverSafely()
    removeOverlaySafely()
    SpeedMeterStateHolder.setServiceRunning(false)
    super.onDestroy()
  }

  private fun promoteToForeground(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
          }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(speedSummary: String): Notification {
    val openAppIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val openAppPendingIntent =
      PendingIntent.getActivity(
        this,
        REQUEST_CODE_OPEN_APP,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent = Intent(this, SpeedOverlayService::class.java).apply { action = ACTION_STOP }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        REQUEST_CODE_STOP_SERVICE,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_speed_meter)
      .setContentTitle(getString(R.string.notif_title))
      .setContentText(speedSummary)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(openAppPendingIntent)
      .addAction(0, getString(R.string.notif_action_stop), stopPendingIntent)
      .build()
  }

  private fun attachOverlayWindow() {
    val config = preferences.current()
    val density = resources.displayMetrics.density

    val bg =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 14f * density
        setColor(Color.argb((config.opacity * 235).roundToInt(), 11, 17, 30))
        setStroke((1f * density).roundToInt().coerceAtLeast(1), Color.argb(110, 34, 211, 238))
      }
    overlayBackground = bg

    val container =
      LinearLayout(this).apply {
        background = bg
        gravity = Gravity.CENTER_VERTICAL
        elevation = 6f * density
      }

    val dlView =
      TextView(this).apply {
        setTextColor(Color.parseColor("#22D3EE"))
        typeface = monoTypeface ?: Typeface.MONOSPACE
        includeFontPadding = false
        setShadowLayer(2f * density, 0f, 1f * density, Color.argb(190, 0, 0, 0))
        text = "↓ 0 B/s"
      }

    val ulView =
      TextView(this).apply {
        setTextColor(Color.parseColor("#FBBF24"))
        typeface = monoTypeface ?: Typeface.MONOSPACE
        includeFontPadding = false
        setShadowLayer(2f * density, 0f, 1f * density, Color.argb(190, 0, 0, 0))
        text = "↑ 0 B/s"
      }

    val pingView =
      TextView(this).apply {
        setTextColor(Color.parseColor("#CBD5E1"))
        typeface = monoTypeface ?: Typeface.MONOSPACE
        includeFontPadding = false
        setShadowLayer(2f * density, 0f, 1f * density, Color.argb(190, 0, 0, 0))
        visibility = View.GONE
      }

    container.addView(dlView)
    container.addView(ulView)
    container.addView(pingView)

    overlayContainer = container
    downloadTextView = dlView
    uploadTextView = ulView
    pingTextView = pingView

    val windowType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
      }

    val params =
      WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          windowType,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        )
        .apply {
          gravity = Gravity.TOP or Gravity.START
          x = clampX(config.overlayX)
          y = clampY(config.overlayY)
        }

    overlayParams = params
    applyOverlayAppearance(config)
    attachDragListener(container, params)

    try {
      windowManager.addView(container, params)
    } catch (e: SecurityException) {
      SpeedMeterStateHolder.postAlert(getString(R.string.error_overlay_permission_required))
      stopServiceCleanly()
    } catch (e: WindowManager.BadTokenException) {
      SpeedMeterStateHolder.postAlert(getString(R.string.error_overlay_permission_required))
      stopServiceCleanly()
    }
  }

  private fun attachDragListener(view: View, params: WindowManager.LayoutParams) {
    val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f
    var isDragging = false

    view.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          initialX = params.x
          initialY = params.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          isDragging = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - initialTouchX).roundToInt()
          val dy = (event.rawY - initialTouchY).roundToInt()
          if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
            isDragging = true
          }
          if (isDragging && v.isAttachedToWindow) {
            params.x = clampX(initialX + dx)
            params.y = clampY(initialY + dy)
            runCatching { windowManager.updateViewLayout(v, params) }
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (isDragging) {
            preferences.saveOverlayPosition(params.x, params.y)
          } else {
            v.performClick()
            val launchIntent =
              Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
              }
            startActivity(launchIntent)
          }
          true
        }
        else -> false
      }
    }
  }

  private fun applyOverlayAppearance(config: MeterConfig) {
    val container = overlayContainer ?: return
    val dlView = downloadTextView ?: return
    val ulView = uploadTextView ?: return
    val pingView = pingTextView ?: return

    val density = resources.displayMetrics.density
    val hPad = (config.size.horizontalPaddingDp * density).roundToInt()
    val vPad = (config.size.verticalPaddingDp * density).roundToInt()

    container.setPadding(hPad, vPad, hPad, vPad)
    container.orientation =
      if (config.layout == OverlayLayout.STACKED) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

    val textSp = config.size.textSizeSp
    dlView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
    ulView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
    pingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (textSp - 1.5f).coerceAtLeast(8.5f))

    val gap = (6 * density).roundToInt()
    val ulParams =
      (ulView.layoutParams as? LinearLayout.LayoutParams)
        ?: LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    if (config.layout == OverlayLayout.INLINE) {
      ulParams.setMargins(gap, 0, 0, 0)
    } else {
      ulParams.setMargins(0, (2 * density).roundToInt(), 0, 0)
    }
    ulView.layoutParams = ulParams

    val pingParams =
      (pingView.layoutParams as? LinearLayout.LayoutParams)
        ?: LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    if (config.layout == OverlayLayout.INLINE) {
      pingParams.setMargins(gap, 0, 0, 0)
    } else {
      pingParams.setMargins(0, (2 * density).roundToInt(), 0, 0)
    }
    pingView.layoutParams = pingParams

    val bgAlpha = (config.opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    val borderAlpha =
      if (config.opacity <= 0.05f) 0 else (config.opacity * 150f).roundToInt().coerceIn(0, 150)

    container.elevation = if (config.opacity > 0.12f) 6f * density * config.opacity else 0f
    overlayBackground?.apply {
      setColor(Color.argb(bgAlpha, 11, 17, 30))
      setStroke(
        (1f * density).roundToInt().coerceAtLeast(1),
        Color.argb(borderAlpha, 34, 211, 238),
      )
    }

    val latest = SpeedMeterStateHolder.telemetry.value
    renderOverlayText(latest, config)
  }

  private fun applySavedPosition() {
    val container = overlayContainer ?: return
    val params = overlayParams ?: return
    if (!container.isAttachedToWindow) return

    val config = preferences.current()
    params.x = clampX(config.overlayX)
    params.y = clampY(config.overlayY)
    runCatching { windowManager.updateViewLayout(container, params) }
  }

  private fun startConfigObserverIfNeeded() {
    if (configObserverJob?.isActive == true) return
    configObserverJob =
      serviceScope.launch {
        preferences.config.collect { updatedConfig -> applyOverlayAppearance(updatedConfig) }
      }
  }

  private fun startPollingLoopIfNeeded() {
    if (pollingJob?.isActive == true) return
    speedSampler.resetBaseline()

    pollingJob =
      serviceScope.launch {
        while (isActive && !isStopping) {
          // Suspend polling completely while the screen is off for minimal battery usage
          if (!isScreenInteractive.value) {
            isScreenInteractive.first { it }
            speedSampler.resetBaseline()
          }

          if (!Settings.canDrawOverlays(this@SpeedOverlayService)) {
            SpeedMeterStateHolder.postAlert(getString(R.string.error_overlay_revoked))
            stopServiceCleanly()
            break
          }

          val config = preferences.current()
          val snapshot =
            withContext(Dispatchers.Default) {
              val delta = speedSampler.sampleTrafficDelta()
              val netInfo = speedSampler.readNetworkInfo()

              sessionDownloadBytes += delta.deltaRxBytes
              sessionUploadBytes += delta.deltaTxBytes

              if (delta.downloadBytesPerSec > peakDownloadBps) {
                peakDownloadBps = delta.downloadBytesPerSec
              }
              if (delta.uploadBytesPerSec > peakUploadBps) {
                peakUploadBps = delta.uploadBytesPerSec
              }

              // Probe Tenda F3 gateway periodically (every 5 ticks) on Wi-Fi without blocking
              if (
                config.probeTendaRouter &&
                  netInfo.transportType == TransportType.WIFI &&
                  tickCounter % ROUTER_PROBE_STRIDE == 0
              ) {
                val targetHost =
                  config.routerIp.ifBlank { netInfo.detectedGatewayIp ?: MeterConfig.DEFAULT_ROUTER_IP }
                lastTendaProbe = tendaClient.probeRouter(targetHost, config.routerPassword)
              } else if (!config.probeTendaRouter || netInfo.transportType != TransportType.WIFI) {
                lastTendaProbe = null
              }
              tickCounter++

              val sample =
                SpeedSample(
                  downloadBytesPerSec = delta.downloadBytesPerSec,
                  uploadBytesPerSec = delta.uploadBytesPerSec,
                  timestampMs = SystemClock.elapsedRealtime(),
                )
              if (sampleHistory.size >= MAX_HISTORY_SAMPLES) {
                sampleHistory.removeFirst()
              }
              sampleHistory.addLast(sample)

              TelemetrySnapshot(
                downloadBytesPerSec = delta.downloadBytesPerSec,
                uploadBytesPerSec = delta.uploadBytesPerSec,
                peakDownloadBytesPerSec = peakDownloadBps,
                peakUploadBytesPerSec = peakUploadBps,
                sessionDownloadBytes = sessionDownloadBytes,
                sessionUploadBytes = sessionUploadBytes,
                transportType = netInfo.transportType,
                isInternetValidated = netInfo.isInternetValidated,
                detectedGatewayIp = netInfo.detectedGatewayIp,
                wifiLinkSpeedMbps = netInfo.wifiLinkSpeedMbps,
                wifiSignalDbm = netInfo.wifiSignalDbm,
                gatewayPingMs = lastTendaProbe?.pingMs,
                isTendaRouterDetected = lastTendaProbe?.isTendaF3Detected == true,
                routerStatusLabel =
                  lastTendaProbe?.statusMessage ?: "Device TrafficStats (${netInfo.transportType.displayName})",
                history = sampleHistory.toList(),
              )
            }

          SpeedMeterStateHolder.updateTelemetry(snapshot)
          renderOverlayText(snapshot, config)

          // Update ongoing notification every 2nd tick to keep SystemUI overhead very low
          if (tickCounter % 2 == 1) {
            val dl = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, config.unit)
            val ul = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, config.unit)
            notificationManager.notify(NOTIFICATION_ID, buildNotification("↓ $dl  ·  ↑ $ul"))
          }

          delay(config.refreshInterval.millis)
        }
      }
  }

  private fun renderOverlayText(snapshot: TelemetrySnapshot, config: MeterConfig) {
    val dl = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, config.unit)
    val ul = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, config.unit)

    downloadTextView?.text = "↓ $dl"
    uploadTextView?.text = "↑ $ul"

    val pingMs = snapshot.gatewayPingMs
    if (config.showGatewayPing && pingMs != null) {
      pingTextView?.visibility = View.VISIBLE
      pingTextView?.text = "${pingMs}ms"
    } else if (config.showGatewayPing && snapshot.transportType == TransportType.NONE) {
      pingTextView?.visibility = View.VISIBLE
      pingTextView?.text = "Offline"
    } else {
      pingTextView?.visibility = View.GONE
    }
  }

  private fun clampX(rawX: Int): Int {
    val maxWidth = (resources.displayMetrics.widthPixels - 120).coerceAtLeast(0)
    return rawX.coerceIn(0, maxWidth)
  }

  private fun clampY(rawY: Int): Int {
    val maxHeight = (resources.displayMetrics.heightPixels - 120).coerceAtLeast(0)
    return rawY.coerceIn(0, maxHeight)
  }

  private fun registerScreenReceiver() {
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_SCREEN_ON)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(screenStateReceiver, filter)
    }
  }

  private fun unregisterScreenReceiverSafely() {
    runCatching { unregisterReceiver(screenStateReceiver) }
  }

  private fun removeOverlaySafely() {
    val container = overlayContainer ?: return
    if (container.isAttachedToWindow) {
      runCatching { windowManager.removeViewImmediate(container) }
    }
    overlayContainer = null
    downloadTextView = null
    uploadTextView = null
    pingTextView = null
    overlayParams = null
  }

  private fun stopServiceCleanly() {
    isStopping = true
    pollingJob?.cancel()
    removeOverlaySafely()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION") stopForeground(true)
    }
    SpeedMeterStateHolder.setServiceRunning(false)
    stopSelf()
  }

  companion object {
    const val ACTION_START = "com.example.action.START_SPEED_OVERLAY"
    const val ACTION_STOP = "com.example.action.STOP_SPEED_OVERLAY"
    const val ACTION_RESET_POSITION = "com.example.action.RESET_OVERLAY_POSITION"
    const val ACTION_REFRESH_CONFIG = "com.example.action.REFRESH_OVERLAY_CONFIG"

    private const val CHANNEL_ID = "speed_overlay_channel"
    private const val NOTIFICATION_ID = 3011
    private const val REQUEST_CODE_OPEN_APP = 101
    private const val REQUEST_CODE_STOP_SERVICE = 102
    private const val MAX_HISTORY_SAMPLES = 30
    private const val ROUTER_PROBE_STRIDE = 5

    fun createStartIntent(context: Context): Intent =
      Intent(context, SpeedOverlayService::class.java).apply { action = ACTION_START }

    fun createStopIntent(context: Context): Intent =
      Intent(context, SpeedOverlayService::class.java).apply { action = ACTION_STOP }

    fun createResetPositionIntent(context: Context): Intent =
      Intent(context, SpeedOverlayService::class.java).apply { action = ACTION_RESET_POSITION }
  }
}
