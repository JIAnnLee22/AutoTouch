package jiannlee22.funnytool

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun OverlayToolbar(service: TapAccessibilityService, move: (Float, Float) -> Unit) {
    val runtime by TapAccessibilityService.state.collectAsStateWithLifecycle()
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp, modifier = Modifier.sizeIn(maxWidth = 340.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("⠿  FunnyTool · ${runtime.completed} 次完成 · 拖动此处",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures { change, amount -> change.consume(); move(amount.x, amount.y) }
                }.padding(vertical = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (runtime.mode == RunMode.EDITING) {
                    Button(onClick = service::saveEditor) { Text("保存") }
                    TextButton(onClick = { service.stop("已取消编辑") }) { Text("取消") }
                } else {
                    Button(onClick = { if (runtime.mode == RunMode.RUNNING) service.pause() else service.startRun() }) {
                        Text(if (runtime.mode == RunMode.RUNNING) "暂停" else "恢复")
                    }
                    TextButton(onClick = { service.stop("已停止，待执行事件已清空") }) { Text("停止") }
                    TextButton(onClick = service::editRegions) { Text("编辑") }
                }
            }
        }
    }
}

@Composable
fun RegionEditor(config: AppConfig, onChange: (AppConfig) -> Unit) {
    val current by rememberUpdatedState(config)
    val changeConfig by rememberUpdatedState(onChange)
    val view = LocalView.current
    val density = LocalDensity.current.density
    val handlePx = 22 * density
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 16 * density
        setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
    } }
    fun origin(): Offset {
        val location = IntArray(2).also(view::getLocationOnScreen)
        return Offset(location[0].toFloat(), location[1].toFloat())
    }
    Canvas(Modifier.fillMaxSize().background(Color(0x55101D2D)).pointerInput(Unit) {
        var selectedId: String? = null
        var resizing = false
        detectDragGestures(
            onDragStart = { local ->
                val point = local + origin()
                val region = current.regions.asReversed().firstOrNull {
                    hypot(point.x - it.centerX, point.y - it.centerY) <= it.radiusPx + handlePx
                }
                selectedId = region?.id
                resizing = region != null && abs(hypot(point.x - region.centerX, point.y - region.centerY) - region.radiusPx) < handlePx
            },
            onDragEnd = { selectedId = null },
            onDragCancel = { selectedId = null },
        ) { pointer, delta ->
            pointer.consume()
            val screen = current.screen ?: return@detectDragGestures
            val position = pointer.position + origin()
            changeConfig(current.copy(regions = current.regions.map { region ->
                if (region.id != selectedId) region else {
                    if (resizing) {
                        val maxRadius = minOf(region.centerX - screen.leftPx, region.centerY - screen.topPx,
                            screen.rightPx - 1 - region.centerX, screen.bottomPx - 1 - region.centerY)
                        region.copy(radiusPx = hypot(position.x - region.centerX, position.y - region.centerY)
                            .coerceIn(1f, maxRadius))
                    } else region.copy(centerX = region.centerX + delta.x, centerY = region.centerY + delta.y).fitted(screen)
                }
            }))
        }
    }) {
        val offset = origin()
        config.regions.forEach { region ->
            val center = Offset(region.centerX, region.centerY) - offset
            val color = if (region.enabled) Color(0xFF5BE6BC) else Color(0xFFAFBAC8)
            drawCircle(color.copy(alpha = .18f), region.radiusPx, center)
            drawCircle(color, region.radiusPx, center, style = Stroke(2 * density))
            drawCircle(color, 4 * density, center)
            drawCircle(Color(0xFFFF8C70), 7 * density, center + Offset(region.radiusPx, 0f))
            drawContext.canvas.nativeCanvas.drawText(region.name, center.x - region.radiusPx / 2,
                center.y - 14 * density, paint)
        }
    }
}
