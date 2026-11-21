package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.SpeedMeterScreen
import com.example.ui.theme.TendaF3SpeedMeterTheme

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      TendaF3SpeedMeterTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current

        val overlaySettingsLauncher =
          rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshPermissions()
          }

        val notificationPermissionLauncher =
          rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
          }

        // Re-check overlay & notification permissions immediately when returning from Settings
        DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
              viewModel.refreshPermissions()
            }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val launchOverlaySettings: () -> Unit = {
          val intent =
            Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
              Uri.parse("package:$packageName"),
            )
          overlaySettingsLauncher.launch(intent)
        }

        val launchNotificationPermission: () -> Unit = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }

        SpeedMeterScreen(
          uiState = uiState,
          onStartOverlay = {
            val started = viewModel.startOverlayService()
            if (!started) {
              launchOverlaySettings()
            }
          },
          onStopOverlay = { viewModel.stopOverlayService() },
          onRequestOverlayPermission = launchOverlaySettings,
          onRequestNotificationPermission = launchNotificationPermission,
          onDismissAlert = { viewModel.dismissAlert() },
          onResetPosition = { viewModel.resetOverlayPosition() },
          onUpdateUnit = { viewModel.updateSpeedUnit(it) },
          onUpdateSize = { viewModel.updateOverlaySize(it) },
          onUpdateLayout = { viewModel.updateOverlayLayout(it) },
          onUpdateOpacity = { viewModel.updateOpacity(it) },
          onUpdateRefreshInterval = { viewModel.updateRefreshInterval(it) },
          onUpdateShowPing = { viewModel.updateShowGatewayPing(it) },
          onUpdateProbeRouter = { viewModel.updateProbeTendaRouter(it) },
          onDetectGateway = { viewModel.detectAndApplyGatewayIp() },
          onTestRouter = { ip, password -> viewModel.testRouterConnection(ip, password) },
          onSaveRouterConfig = { ip, password -> viewModel.updateRouterCredentials(ip, password) },
        )
      }
    }
  }
}
