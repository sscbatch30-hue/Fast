package com.example.data

enum class SpeedUnit {
  AUTO_BYTES,
  BITS_PER_SEC,
}

enum class OverlaySize(val textSizeSp: Float, val horizontalPaddingDp: Int, val verticalPaddingDp: Int) {
  COMPACT(textSizeSp = 10.5f, horizontalPaddingDp = 8, verticalPaddingDp = 4),
  STANDARD(textSizeSp = 12f, horizontalPaddingDp = 11, verticalPaddingDp = 6),
  LARGE(textSizeSp = 14f, horizontalPaddingDp = 14, verticalPaddingDp = 8),
}

enum class OverlayLayout {
  STACKED,
  INLINE,
}

enum class RefreshInterval(val millis: Long, val shortLabel: String) {
  FAST_1S(1000L, "1.0s"),
  BALANCED_2S(2000L, "2.0s"),
  ECO_3S(3000L, "3.0s"),
}

data class MeterConfig(
  val unit: SpeedUnit = SpeedUnit.AUTO_BYTES,
  val size: OverlaySize = OverlaySize.STANDARD,
  val layout: OverlayLayout = OverlayLayout.STACKED,
  val opacity: Float = 0.88f,
  val refreshInterval: RefreshInterval = RefreshInterval.FAST_1S,
  val showGatewayPing: Boolean = false,
  val probeTendaRouter: Boolean = true,
  val routerIp: String = DEFAULT_ROUTER_IP,
  val routerPassword: String = "",
  val overlayX: Int = DEFAULT_OVERLAY_X,
  val overlayY: Int = DEFAULT_OVERLAY_Y,
) {
  companion object {
    const val DEFAULT_ROUTER_IP = "192.168.0.1"
    const val DEFAULT_OVERLAY_X = 32
    const val DEFAULT_OVERLAY_Y = 140
  }
}
