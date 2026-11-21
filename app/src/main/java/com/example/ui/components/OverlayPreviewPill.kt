package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeterConfig
import com.example.data.OverlayLayout
import com.example.telemetry.SpeedFormatter
import com.example.telemetry.TelemetrySnapshot
import com.example.ui.theme.AmberLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.JetBrainsMonoFontFamily

/**
 * Real-time in-app visual preview mirroring the exact styling, opacity, layout,
 * and typography of the floating SpeedOverlayService pill.
 */
@Composable
fun OverlayPreviewPill(
  config: MeterConfig,
  telemetry: TelemetrySnapshot,
  modifier: Modifier = Modifier,
) {
  val dlText = "↓ ${SpeedFormatter.formatSpeed(telemetry.downloadBytesPerSec, config.unit)}"
  val ulText = "↑ ${SpeedFormatter.formatSpeed(telemetry.uploadBytesPerSec, config.unit)}"
  val pingText =
    when {
      !config.showGatewayPing -> null
      telemetry.gatewayPingMs != null -> "${telemetry.gatewayPingMs}ms"
      else -> "LAN"
    }

  val clampedOpacity = config.opacity.coerceIn(0f, 1f)
  val pillBg = Color(0xFF0B111E).copy(alpha = clampedOpacity)
  val pillBorder =
    if (clampedOpacity <= 0.05f) Color.Transparent
    else CyanAccent.copy(alpha = (clampedOpacity * 0.58f).coerceIn(0f, 0.7f))
  val fontSize = config.size.textSizeSp.sp

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = pillBg,
    border = BorderStroke(1.dp, pillBorder),
    shadowElevation = if (clampedOpacity > 0.12f) 4.dp else 0.dp,
  ) {
    val paddingMod =
      Modifier.padding(
        horizontal = config.size.horizontalPaddingDp.dp,
        vertical = config.size.verticalPaddingDp.dp,
      )

    if (config.layout == OverlayLayout.STACKED) {
      Column(
        modifier = paddingMod,
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = dlText,
          color = CyanAccent,
          fontSize = fontSize,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = ulText,
          color = AmberLight,
          fontSize = fontSize,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Medium,
        )
        if (pingText != null) {
          Text(
            text = pingText,
            color = Color(0xFF94A3B8),
            fontSize = (config.size.textSizeSp - 1.5f).coerceAtLeast(8.5f).sp,
            fontFamily = JetBrainsMonoFontFamily,
          )
        }
      }
    } else {
      Row(
        modifier = paddingMod,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = dlText,
          color = CyanAccent,
          fontSize = fontSize,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = ulText,
          color = AmberLight,
          fontSize = fontSize,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Medium,
        )
        if (pingText != null) {
          Text(
            text = pingText,
            color = Color(0xFF94A3B8),
            fontSize = (config.size.textSizeSp - 1.5f).coerceAtLeast(8.5f).sp,
            fontFamily = JetBrainsMonoFontFamily,
          )
        }
      }
    }
  }
}
