package com.example.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.OverlayLayout
import com.example.data.OverlaySize
import com.example.data.RefreshInterval
import com.example.data.SpeedUnit
import com.example.telemetry.SpeedFormatter
import com.example.telemetry.TransportType
import com.example.ui.components.OverlayPreviewPill
import com.example.ui.components.SpeedChart
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedMeterScreen(
  uiState: MainUiState,
  onStartOverlay: () -> Unit,
  onStopOverlay: () -> Unit,
  onRequestOverlayPermission: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onDismissAlert: () -> Unit,
  onResetPosition: () -> Unit,
  onUpdateUnit: (SpeedUnit) -> Unit,
  onUpdateSize: (OverlaySize) -> Unit,
  onUpdateLayout: (OverlayLayout) -> Unit,
  onUpdateOpacity: (Float) -> Unit,
  onUpdateRefreshInterval: (RefreshInterval) -> Unit,
  onUpdateShowPing: (Boolean) -> Unit,
  onUpdateProbeRouter: (Boolean) -> Unit,
  onDetectGateway: () -> String?,
  onTestRouter: (String, String) -> Unit,
  onSaveRouterConfig: (String, String) -> Unit,
) {
  var showGuideDialog by rememberSaveable { mutableStateOf(false) }
  var routerIpDraft by
    rememberSaveable(uiState.config.routerIp) { mutableStateOf(uiState.config.routerIp) }
  var routerPasswordDraft by
    rememberSaveable(uiState.config.routerPassword) { mutableStateOf(uiState.config.routerPassword) }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
          ),
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.size(36.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Speed,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        },
        actions = {
          StatusBadge(
            isRunning = uiState.isServiceRunning,
            isPausedScreenOff = uiState.isPausedScreenOff,
          )
          IconButton(
            onClick = { showGuideDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("guide_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
              contentDescription = stringResource(R.string.action_guide),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Optional service alert banner (e.g., permission revoked while active)
        if (uiState.serviceAlert != null) {
          item(key = "alert_banner") {
            AlertBanner(message = uiState.serviceAlert, onDismiss = onDismissAlert)
          }
        }

        // 1. Hero Telemetry & Start/Stop Card
        item(key = "hero_card") {
          HeroTelemetryCard(
            uiState = uiState,
            onStartOverlay = onStartOverlay,
            onStopOverlay = onStopOverlay,
            onUpdateOpacity = onUpdateOpacity,
          )
        }

        // 2. Runtime Permissions Readiness Card
        item(key = "permissions_card") {
          PermissionsCard(
            hasOverlayPermission = uiState.hasOverlayPermission,
            hasNotificationPermission = uiState.hasNotificationPermission,
            onRequestOverlay = onRequestOverlayPermission,
            onRequestNotifications = onRequestNotificationPermission,
          )
        }

        // 3. Overlay Appearance & Sampling Config Card
        item(key = "config_card") {
          OverlayConfigCard(
            uiState = uiState,
            onResetPosition = onResetPosition,
            onUpdateUnit = onUpdateUnit,
            onUpdateSize = onUpdateSize,
            onUpdateLayout = onUpdateLayout,
            onUpdateRefreshInterval = onUpdateRefreshInterval,
            onUpdateShowPing = onUpdateShowPing,
          )
        }

        // 4. Tenda F3 Router / Gateway Monitor Card
        item(key = "router_card") {
          TendaRouterCard(
            uiState = uiState,
            routerIpDraft = routerIpDraft,
            routerPasswordDraft = routerPasswordDraft,
            passwordVisible = passwordVisible,
            onRouterIpChange = {
              routerIpDraft = it
              onSaveRouterConfig(it, routerPasswordDraft)
            },
            onRouterPasswordChange = {
              routerPasswordDraft = it
              onSaveRouterConfig(routerIpDraft, it)
            },
            onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
            onToggleProbe = onUpdateProbeRouter,
            onDetectGateway = {
              val detected = onDetectGateway()
              if (!detected.isNullOrBlank()) {
                routerIpDraft = detected
              }
            },
            onTestRouter = { onTestRouter(routerIpDraft, routerPasswordDraft) },
          )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }

  if (showGuideDialog) {
    QuickGuideDialog(onDismiss = { showGuideDialog = false })
  }
}

@Composable
private fun StatusBadge(isRunning: Boolean, isPausedScreenOff: Boolean) {
  val containerColor by
    animateColorAsState(
      targetValue =
        when {
          isPausedScreenOff -> MaterialTheme.colorScheme.secondaryContainer
          isRunning -> MaterialTheme.colorScheme.tertiaryContainer
          else -> MaterialTheme.colorScheme.surfaceVariant
        },
      label = "status_bg",
    )
  val contentColor by
    animateColorAsState(
      targetValue =
        when {
          isPausedScreenOff -> MaterialTheme.colorScheme.onSecondaryContainer
          isRunning -> MaterialTheme.colorScheme.tertiary
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
      label = "status_fg",
    )

  Surface(
    shape = RoundedCornerShape(50),
    color = containerColor,
    modifier = Modifier.padding(end = 4.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(contentColor))
      Text(
        text =
          when {
            isPausedScreenOff -> stringResource(R.string.status_paused_screen_off)
            isRunning -> stringResource(R.string.status_active)
            else -> stringResource(R.string.status_stopped)
          },
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun AlertBanner(message: String, onDismiss: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.errorContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("service_alert_banner"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Rounded.WarningAmber,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("dismiss_alert_button"),
      ) {
        Text(stringResource(R.string.action_close))
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroTelemetryCard(
  uiState: MainUiState,
  onStartOverlay: () -> Unit,
  onStopOverlay: () -> Unit,
  onUpdateOpacity: (Float) -> Unit,
) {
  val snapshot = uiState.telemetry
  val unit = uiState.config.unit
  val dlColor = MaterialTheme.colorScheme.primary
  val ulColor = MaterialTheme.colorScheme.secondary

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier =
        Modifier.background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f),
                  Color.Transparent,
                )
            )
          )
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Network transport & link header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector =
              when (snapshot.transportType) {
                TransportType.WIFI -> Icons.Rounded.Wifi
                TransportType.CELLULAR -> Icons.Rounded.SignalCellularAlt
                else -> Icons.Rounded.NetworkCheck
              },
            contentDescription = null,
            tint = dlColor,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text =
              buildString {
                append(snapshot.transportType.displayName)
                snapshot.detectedGatewayIp?.let { append(" · $it") }
                snapshot.wifiLinkSpeedMbps?.let { append(" · ${it}Mbps") }
              },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        snapshot.gatewayPingMs?.let { ping ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = "${ping} ms",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      // Live Download & Upload readouts
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SpeedReadoutBlock(
          label = stringResource(R.string.label_download),
          speedText = SpeedFormatter.formatSpeed(snapshot.downloadBytesPerSec, unit),
          peakText =
            "${stringResource(R.string.label_peak_dl)}: ${SpeedFormatter.formatSpeed(snapshot.peakDownloadBytesPerSec, unit)}",
          accentColor = dlColor,
          icon = Icons.Rounded.ArrowDownward,
          modifier = Modifier.weight(1f),
        )
        SpeedReadoutBlock(
          label = stringResource(R.string.label_upload),
          speedText = SpeedFormatter.formatSpeed(snapshot.uploadBytesPerSec, unit),
          peakText =
            "${stringResource(R.string.label_peak_ul)}: ${SpeedFormatter.formatSpeed(snapshot.peakUploadBytesPerSec, unit)}",
          accentColor = ulColor,
          icon = Icons.Rounded.ArrowUpward,
          modifier = Modifier.weight(1f),
        )
      }

      // Real-time sparkline chart
      SpeedChart(
        samples = snapshot.history,
        downloadColor = dlColor,
        uploadColor = ulColor,
      )

      // Session total & status strip
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TelemetryMiniChip(
          label =
            "${stringResource(R.string.label_session_total)} ↓ ${SpeedFormatter.formatDataTotal(snapshot.sessionDownloadBytes)}"
        )
        TelemetryMiniChip(
          label =
            "${stringResource(R.string.label_session_total)} ↑ ${SpeedFormatter.formatDataTotal(snapshot.sessionUploadBytes)}"
        )
        TelemetryMiniChip(label = snapshot.routerStatusLabel)
      }

      // Primary Start / Stop CTA button
      Button(
        onClick = { if (uiState.isServiceRunning) onStopOverlay() else onStartOverlay() },
        colors =
          ButtonDefaults.buttonColors(
            containerColor =
              if (uiState.isServiceRunning) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
            contentColor =
              if (uiState.isServiceRunning) MaterialTheme.colorScheme.onError
              else MaterialTheme.colorScheme.onPrimary,
          ),
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.fillMaxWidth()
            .height(52.dp)
            .minimumInteractiveComponentSize()
            .testTag("toggle_overlay_button"),
      ) {
        Icon(
          imageVector =
            if (uiState.isServiceRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
          contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text =
            if (uiState.isServiceRunning) stringResource(R.string.action_stop_overlay)
            else stringResource(R.string.action_start_overlay),
          style = MaterialTheme.typography.labelLarge,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Overlay Background Opacity Slider (0% - 100%) with live preview
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = stringResource(R.string.label_opacity),
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text = "${(uiState.config.opacity * 100).roundToInt()}%",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          OverlayPreviewPill(config = uiState.config, telemetry = uiState.telemetry)
        }
        Slider(
          value = uiState.config.opacity,
          onValueChange = onUpdateOpacity,
          valueRange = 0.0f..1.0f,
          modifier = Modifier.fillMaxWidth().testTag("opacity_slider"),
        )
      }
    }
  }
}

@Composable
private fun SpeedReadoutBlock(
  label: String,
  speedText: String,
  peakText: String,
  accentColor: Color,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = speedText,
        style = MaterialTheme.typography.titleLarge,
        fontFamily = MaterialTheme.typography.displaySmall.fontFamily,
        color = accentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = peakText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun TelemetryMiniChip(label: String) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
    )
  }
}

@Composable
private fun PermissionsCard(
  hasOverlayPermission: Boolean,
  hasNotificationPermission: Boolean,
  onRequestOverlay: () -> Unit,
  onRequestNotifications: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Layers,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = stringResource(R.string.section_permissions),
          style = MaterialTheme.typography.titleMedium,
        )
      }

      PermissionRow(
        icon = Icons.Rounded.Layers,
        title = stringResource(R.string.perm_overlay_title),
        description = stringResource(R.string.perm_overlay_desc),
        granted = hasOverlayPermission,
        buttonTag = "grant_overlay_button",
        onGrantClick = onRequestOverlay,
      )

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PermissionRow(
          icon = Icons.Rounded.NotificationsActive,
          title = stringResource(R.string.perm_notifications_title),
          description = stringResource(R.string.perm_notifications_desc),
          granted = hasNotificationPermission,
          buttonTag = "grant_notification_button",
          onGrantClick = onRequestNotifications,
        )
      }
    }
  }
}

@Composable
private fun PermissionRow(
  icon: ImageVector,
  title: String,
  description: String,
  granted: Boolean,
  buttonTag: String,
  onGrantClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
      contentDescription = null,
      tint =
        if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
      modifier = Modifier.size(22.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.labelLarge)
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (granted) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
      ) {
        Text(
          text = stringResource(R.string.action_granted),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    } else {
      Button(
        onClick = onGrantClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag(buttonTag),
      ) {
        Text(stringResource(R.string.action_grant))
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayConfigCard(
  uiState: MainUiState,
  onResetPosition: () -> Unit,
  onUpdateUnit: (SpeedUnit) -> Unit,
  onUpdateSize: (OverlaySize) -> Unit,
  onUpdateLayout: (OverlayLayout) -> Unit,
  onUpdateRefreshInterval: (RefreshInterval) -> Unit,
  onUpdateShowPing: (Boolean) -> Unit,
) {
  val config = uiState.config

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(R.string.section_overlay_config),
            style = MaterialTheme.typography.titleMedium,
          )
        }

        OutlinedButton(
          onClick = onResetPosition,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("reset_position_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.action_reset_position),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }

      // Live Overlay Preview Area
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = stringResource(R.string.label_live_preview),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OverlayPreviewPill(config = config, telemetry = uiState.telemetry)
          Text(
            text = stringResource(R.string.hint_drag_overlay),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Speed Unit selector
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.label_speed_unit),
          style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = config.unit == SpeedUnit.AUTO_BYTES,
            onClick = { onUpdateUnit(SpeedUnit.AUTO_BYTES) },
            label = { Text(stringResource(R.string.unit_auto_bytes)) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("unit_bytes_chip"),
          )
          FilterChip(
            selected = config.unit == SpeedUnit.BITS_PER_SEC,
            onClick = { onUpdateUnit(SpeedUnit.BITS_PER_SEC) },
            label = { Text(stringResource(R.string.unit_bits)) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("unit_bits_chip"),
          )
        }
      }

      // Refresh Interval selector
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.label_refresh_rate),
          style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          RefreshInterval.entries.forEach { interval ->
            FilterChip(
              selected = config.refreshInterval == interval,
              onClick = { onUpdateRefreshInterval(interval) },
              label = { Text(interval.shortLabel) },
              modifier = Modifier.minimumInteractiveComponentSize(),
            )
          }
        }
      }

      // Pill Size selector
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.label_overlay_size),
          style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OverlaySize.entries.forEach { sizeOption ->
            FilterChip(
              selected = config.size == sizeOption,
              onClick = { onUpdateSize(sizeOption) },
              label = {
                Text(sizeOption.name.lowercase().replaceFirstChar { it.uppercase() })
              },
              modifier = Modifier.minimumInteractiveComponentSize(),
            )
          }
        }
      }

      // Pill Layout selector
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.label_overlay_layout),
          style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = config.layout == OverlayLayout.STACKED,
            onClick = { onUpdateLayout(OverlayLayout.STACKED) },
            label = { Text(stringResource(R.string.layout_stacked)) },
            modifier = Modifier.minimumInteractiveComponentSize(),
          )
          FilterChip(
            selected = config.layout == OverlayLayout.INLINE,
            onClick = { onUpdateLayout(OverlayLayout.INLINE) },
            label = { Text(stringResource(R.string.layout_inline)) },
            modifier = Modifier.minimumInteractiveComponentSize(),
          )
        }
      }

      // Show Ping toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.label_show_ping),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f),
        )
        Switch(
          checked = config.showGatewayPing,
          onCheckedChange = onUpdateShowPing,
          modifier = Modifier.testTag("show_ping_switch"),
        )
      }
    }
  }
}

@Composable
private fun TendaRouterCard(
  uiState: MainUiState,
  routerIpDraft: String,
  routerPasswordDraft: String,
  passwordVisible: Boolean,
  onRouterIpChange: (String) -> Unit,
  onRouterPasswordChange: (String) -> Unit,
  onTogglePasswordVisibility: () -> Unit,
  onToggleProbe: (Boolean) -> Unit,
  onDetectGateway: () -> Unit,
  onTestRouter: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Rounded.Router,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(R.string.section_tenda_router),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Switch(
          checked = uiState.config.probeTendaRouter,
          onCheckedChange = onToggleProbe,
          modifier = Modifier.testTag("probe_router_switch"),
        )
      }

      Text(
        text = stringResource(R.string.desc_probe_router),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      AnimatedVisibility(visible = uiState.config.probeTendaRouter) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = routerIpDraft,
              onValueChange = onRouterIpChange,
              label = { Text(stringResource(R.string.label_router_ip)) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              modifier = Modifier.weight(1f).testTag("router_ip_input"),
            )
            OutlinedButton(
              onClick = onDetectGateway,
              enabled = uiState.telemetry.detectedGatewayIp != null,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("detect_gateway_button"),
            ) {
              Text(stringResource(R.string.action_detect_gateway))
            }
          }

          OutlinedTextField(
            value = routerPasswordDraft,
            onValueChange = onRouterPasswordChange,
            label = { Text(stringResource(R.string.label_router_password)) },
            singleLine = true,
            visualTransformation =
              if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(
                onClick = onTogglePasswordVisibility,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector =
                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                  contentDescription = null,
                )
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("router_password_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            OutlinedButton(
              onClick = onTestRouter,
              enabled = !uiState.isTestingRouter,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("test_router_button"),
            ) {
              if (uiState.isTestingRouter) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
              }
              Text(stringResource(R.string.action_test_router))
            }

            uiState.routerTestResult?.let { resultMsg ->
              Text(
                text = resultMsg,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuickGuideDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.app_name)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "1. Grant 'Display over other apps' permission so the speed pill can float cleanly over any app.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = "2. On Android 13+, grant Notifications so the foreground service stays active with a quick Stop button.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = "3. Drag the floating pill anywhere on screen; touches outside pass straight through. Polling automatically pauses when the screen turns off to save battery.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = "বাংলা নির্দেশিকা (README-BN.txt): অ্যাপটি চালু করতে প্রথমে Display over other apps অনুমতি দিন, এরপর Start Live Overlay বাটনে ট্যাপ করুন।",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text(stringResource(R.string.action_close))
      }
    },
  )
}
