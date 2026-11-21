package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.telemetry.SpeedSample
import kotlin.math.max

/**
 * Lightweight Compose Canvas sparkline displaying recent download and upload throughput.
 */
@Composable
fun SpeedChart(
  samples: List<SpeedSample>,
  downloadColor: Color,
  uploadColor: Color,
  modifier: Modifier = Modifier,
) {
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

  Canvas(modifier = modifier.fillMaxWidth().height(84.dp)) {
    val w = size.width
    val h = size.height
    val topPad = 8.dp.toPx()
    val bottomPad = 6.dp.toPx()
    val usableH = (h - topPad - bottomPad).coerceAtLeast(1f)

    // Subtle horizontal guide lines
    for (i in 0..2) {
      val y = topPad + (usableH * i / 2f)
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(w, y),
        strokeWidth = 1.dp.toPx(),
      )
    }

    if (samples.size < 2) return@Canvas

    val maxSpeed =
      samples.maxOfOrNull { max(it.downloadBytesPerSec, it.uploadBytesPerSec) }?.coerceAtLeast(1024L)
        ?: 1024L

    val stepX = w / (samples.size - 1).coerceAtLeast(1)

    val dlLinePath = Path()
    val dlFillPath = Path()
    val ulLinePath = Path()

    samples.forEachIndexed { index, sample ->
      val x = index * stepX
      val dlRatio = (sample.downloadBytesPerSec.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
      val ulRatio = (sample.uploadBytesPerSec.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)

      val dlY = topPad + usableH * (1f - dlRatio)
      val ulY = topPad + usableH * (1f - ulRatio)

      if (index == 0) {
        dlLinePath.moveTo(x, dlY)
        dlFillPath.moveTo(x, h - bottomPad)
        dlFillPath.lineTo(x, dlY)
        ulLinePath.moveTo(x, ulY)
      } else {
        dlLinePath.lineTo(x, dlY)
        dlFillPath.lineTo(x, dlY)
        ulLinePath.lineTo(x, ulY)
      }

      if (index == samples.lastIndex) {
        dlFillPath.lineTo(x, h - bottomPad)
        dlFillPath.close()
      }
    }

    drawPath(
      path = dlFillPath,
      brush =
        Brush.verticalGradient(
          colors = listOf(downloadColor.copy(alpha = 0.28f), downloadColor.copy(alpha = 0.0f)),
          startY = topPad,
          endY = h,
        ),
    )

    drawPath(
      path = ulLinePath,
      color = uploadColor.copy(alpha = 0.85f),
      style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    drawPath(
      path = dlLinePath,
      color = downloadColor,
      style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}
