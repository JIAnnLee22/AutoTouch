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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var collapsed by remember { mutableStateOf(false) }
    val drag = Modifier.pointerInput(Unit) {
        detectDragGestures { change, amount -> change.consume(); move(amount.x, amount.y) }
    }.sizeIn(minHeight = 48.dp).padding(vertical = 12.dp, horizontal = 8.dp)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp, modifier = Modifier.sizeIn(maxWidth = 340.dp)) {
        if (collapsed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${runtime.completed} 次", modifier = drag, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { collapsed = false }, modifier = Modifier.semantics { contentDescription = "展开悬浮窗" }) {
                    Text("▸")
                }
            }
        } else Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FunnyTool · ${runtime.completed}/${runtime.targetTriggerCount} 次", modifier = drag,
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { collapsed = true }, modifier = Modifier.semantics { contentDescription = "折叠悬浮窗" }) {
                    Text("▾")
                }
            }
            Text(runtime.message, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            if (runtime.mode == RunMode.IDLE) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { TapAccessibilityService.setTargetTriggerCount(runtime.targetTriggerCount - 1) },
                    enabled = runtime.targetTriggerCount > 1) { Text("−") }
                Text("本次 ${runtime.targetTriggerCount} 次", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { TapAccessibilityService.setTargetTriggerCount(runtime.targetTriggerCount + 1) },
                    enabled = runtime.targetTriggerCount < Long.MAX_VALUE) { Text("＋") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (runtime.mode == RunMode.EDITING) {
                    Button(onClick = service::saveEditor) { Text("保存") }
                    TextButton(onClick = service::cancelEditor) { Text("取消") }
                } else {
                    Button(onClick = { if (runtime.mode == RunMode.RUNNING) service.pause() else service.startRun() }) {
                        Text(when (runtime.mode) { RunMode.RUNNING -> "暂停"; RunMode.PAUSED -> "恢复"; else -> "开始" })
                    }
                    TextButton(onClick = { service.stop("已停止，本次待执行任务已清空") }) { Text("关闭") }
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
            changeConfig(current.updateSelected { group -> group.copy(regions = group.regions.map { region ->
                if (region.id != selectedId) region else {
                    if (resizing) {
                        val maxRadius = minOf(region.centerX - screen.leftPx, region.centerY - screen.topPx,
                            screen.rightPx - 1 - region.centerX, screen.bottomPx - 1 - region.centerY)
                        region.copy(radiusPx = hypot(position.x - region.centerX, position.y - region.centerY)
                            .coerceIn(1f, maxRadius))
                    } else region.copy(centerX = region.centerX + delta.x, centerY = region.centerY + delta.y).fitted(screen)
                }
            }) })
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
