package jiannlee22.funnytool

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun OverlayToolbar(
    service: TapAccessibilityService,
    initialCollapsed: Boolean = false,
    onCollapsedChange: (Boolean) -> Unit = {},
    move: (Float, Float) -> Unit,
    onMoveEnd: () -> Unit = {},
) {
    val runtime by TapAccessibilityService.state.collectAsStateWithLifecycle()
    var collapsed by remember(initialCollapsed) { mutableStateOf(initialCollapsed) }
    val latestMove by rememberUpdatedState(move)
    val latestOnMoveEnd by rememberUpdatedState(onMoveEnd)
    val latestOnCollapsedChange by rememberUpdatedState(onCollapsedChange)
    val drag = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = { latestOnMoveEnd() },
            onDragCancel = { latestOnMoveEnd() },
        ) { change, amount ->
            change.consume()
            latestMove(amount.x, amount.y)
        }
    }
    if (collapsed && runtime.mode != RunMode.EDITING) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = drag
                    .clickable {
                        collapsed = false
                        latestOnCollapsedChange(false)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = "展开悬浮窗" },
            ) {
                Text(
                    "${runtime.completed}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " ▸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.sizeIn(maxWidth = 340.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "FunnyTool · ${runtime.completed}/${runtime.targetTriggerCount} 次",
                        modifier = drag.padding(vertical = 10.dp, horizontal = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (runtime.mode != RunMode.EDITING) {
                        Text(
                            "▾",
                            modifier = Modifier
                                .clickable {
                                    collapsed = true
                                    latestOnCollapsedChange(true)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .semantics { contentDescription = "折叠悬浮窗" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
}

@Composable
fun RegionEditor(config: AppConfig, onChange: (AppConfig) -> Unit) {
    val current by rememberUpdatedState(config)
    val changeConfig by rememberUpdatedState(onChange)
    val view = LocalView.current
    val density = LocalDensity.current.density
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var barSize by remember { mutableStateOf(IntSize((164 * density).roundToInt(), (72 * density).roundToInt())) }
    var selectedId by remember {
        mutableStateOf(config.regions.firstOrNull { it.enabled }?.id ?: config.regions.firstOrNull()?.id)
    }
    var stemSides by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val activeRegion = config.regions.firstOrNull { it.id == selectedId }
        ?: config.regions.firstOrNull { it.enabled }
        ?: config.regions.firstOrNull()
    val activeId by rememberUpdatedState(activeRegion?.id)

    val screen = config.screen
    val screenMaxRadius = if (screen != null) (minOf(screen.usableWidth, screen.usableHeight) - 1) / 2f else 240f
    val minRadiusPx = minOf(screenMaxRadius, maxOf(12f, 8f * density))
    val maxRadiusCapPx = maxOf(minRadiusPx, minOf(screenMaxRadius, 80f * density))

    val paint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 14 * density
            setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }

    fun defaultSide(region: ClickRegion): Int {
        val midX = if (screen != null) (screen.leftPx + screen.rightPx) / 2f else editorSize.width / 2f
        return if (region.centerX > midX) -1 else 1
    }

    fun updateRegion(id: String, transform: (ClickRegion) -> ClickRegion) {
        val targetScreen = current.screen ?: return
        changeConfig(current.updateSelected { group ->
            group.copy(regions = group.regions.map { region ->
                if (region.id == id) transform(region).fitted(targetScreen) else region
            })
        })
    }

    val activeSide = activeRegion?.let { stemSides[it.id] ?: defaultSide(it) } ?: 1
    val stemGapPx = 16f * density
    val sliderRowCenterFromBottomPx = 22f * density
    val safeLeft = ((screen?.leftPx?.toFloat() ?: 0f) - windowOrigin.x).coerceAtLeast(0f)
    val safeTop = ((screen?.topPx?.toFloat() ?: 0f) - windowOrigin.y).coerceAtLeast(0f)
    val safeRight = ((screen?.rightPx?.toFloat() ?: editorSize.width.toFloat()) - windowOrigin.x)
        .coerceAtMost(editorSize.width.toFloat().coerceAtLeast(safeLeft))
    val safeBottom = ((screen?.bottomPx?.toFloat() ?: editorSize.height.toFloat()) - windowOrigin.y)
        .coerceAtMost(editorSize.height.toFloat().coerceAtLeast(safeTop))

    val latestSafeLeft by rememberUpdatedState(safeLeft)
    val latestSafeRight by rememberUpdatedState(safeRight)
    val latestWindowOrigin by rememberUpdatedState(windowOrigin)
    val latestBarSize by rememberUpdatedState(barSize)

    fun maybeFlipStem(regionId: String) {
        val latest = current.regions.firstOrNull { it.id == regionId } ?: return
        val reach = stemGapPx + latestBarSize.width
        val side = stemSides[regionId] ?: defaultSide(latest)
        val localCx = latest.centerX - latestWindowOrigin.x
        val overflowRight = side > 0 && localCx + reach > latestSafeRight && localCx - reach >= latestSafeLeft
        val overflowLeft = side < 0 && localCx - reach < latestSafeLeft && localCx + reach <= latestSafeRight
        if (overflowRight || overflowLeft) {
            stemSides = stemSides + (regionId to -side)
        }
    }

    val barPosition = activeRegion?.let { region ->
        val localCx = region.centerX - windowOrigin.x
        val localCy = region.centerY - windowOrigin.y
        val desiredX = if (activeSide > 0) localCx + stemGapPx else localCx - stemGapPx - barSize.width
        val desiredY = localCy - (barSize.height - sliderRowCenterFromBottomPx)
        val clampedX = desiredX.coerceIn(safeLeft, maxOf(safeLeft, safeRight - barSize.width))
        val clampedY = desiredY.coerceIn(safeTop, maxOf(safeTop, safeBottom - barSize.height))
        Offset(clampedX, clampedY)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x55101D2D))
            .onGloballyPositioned {
                val loc = IntArray(2).also(view::getLocationOnScreen)
                windowOrigin = Offset(loc[0].toFloat(), loc[1].toFloat())
                editorSize = it.size
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { local ->
                        val point = local + windowOrigin
                        val hit = current.regions.asReversed().firstOrNull {
                            hypot(point.x - it.centerX, point.y - it.centerY) <= maxOf(it.radiusPx, 28f * density)
                        }
                        if (hit != null) selectedId = hit.id
                    }
                }
                .pointerInput(Unit) {
                    var draggingId: String? = null
                    detectDragGestures(
                        onDragStart = { local ->
                            val point = local + windowOrigin
                            val hit = current.regions.asReversed().firstOrNull {
                                hypot(point.x - it.centerX, point.y - it.centerY) <= maxOf(it.radiusPx, 28f * density)
                            }
                            draggingId = hit?.id
                            if (hit != null) selectedId = hit.id
                        },
                        onDragEnd = {
                            draggingId?.let(::maybeFlipStem)
                            draggingId = null
                        },
                        onDragCancel = { draggingId = null },
                    ) { pointer, delta ->
                        val id = draggingId ?: return@detectDragGestures
                        pointer.consume()
                        updateRegion(id) { it.copy(centerX = it.centerX + delta.x, centerY = it.centerY + delta.y) }
                    }
                },
        ) {
            config.regions.forEach { region ->
                val isSelected = region.id == activeRegion?.id
                val center = Offset(region.centerX, region.centerY) - windowOrigin
                val baseColor = if (region.enabled) Color(0xFF5BE6BC) else Color(0xFFAFBAC8)
                drawCircle(baseColor.copy(alpha = if (isSelected) 0.24f else 0.12f), region.radiusPx, center)
                drawCircle(
                    if (isSelected) Color(0xFFFFD166) else baseColor,
                    region.radiusPx,
                    center,
                    style = Stroke((if (isSelected) 2.5f else 1.5f) * density),
                )
                if (isSelected) {
                    val cross = 7f * density
                    drawLine(Color(0xFFFFD166), center - Offset(cross, 0f), center + Offset(cross, 0f), 1.8f * density)
                    drawLine(Color(0xFFFFD166), center - Offset(0f, cross), center + Offset(0f, cross), 1.8f * density)
                    drawCircle(Color(0xFFFFD166), 3.5f * density, center)
                } else {
                    drawCircle(baseColor, 3.5f * density, center)
                    val labelX = (center.x - region.radiusPx / 2f).coerceIn(safeLeft + 4f * density, maxOf(safeLeft + 4f * density, safeRight - 96f * density))
                    val labelY = (center.y - region.radiusPx - 8f * density).coerceAtLeast(safeTop + 16f * density)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${region.name} (点选)",
                        labelX,
                        labelY,
                        paint,
                    )
                }
            }
            if (activeRegion != null && barPosition != null) {
                val center = Offset(activeRegion.centerX, activeRegion.centerY) - windowOrigin
                val attachX = if (activeSide > 0) barPosition.x + 6f * density else barPosition.x + barSize.width - 6f * density
                val attachY = barPosition.y + (barSize.height - sliderRowCenterFromBottomPx)
                drawLine(
                    color = Color(0xFFFFD166),
                    start = center,
                    end = Offset(attachX, attachY),
                    strokeWidth = 2.5f * density,
                    cap = StrokeCap.Round,
                )
                drawCircle(Color(0xFFFFD166), 4f * density, Offset(attachX, attachY))
            }
        }

        if (activeRegion != null && barPosition != null) {
            val regionId = activeRegion.id
            val radiusFraction = ((activeRegion.radiusPx - minRadiusPx) / (maxRadiusCapPx - minRadiusPx).coerceAtLeast(1f))
                .coerceIn(0f, 1f)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .offset { IntOffset(barPosition.x.roundToInt(), barPosition.y.roundToInt()) }
                    .onSizeChanged { barSize = it },
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "${activeRegion.name} · R ${activeRegion.radiusPx.roundToInt()}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .clickable {
                                    stemSides = stemSides + (regionId to -activeSide)
                                }
                                .semantics { contentDescription = "切换操作杆方向" },
                        ) {
                            Text(
                                "⇄",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val sliderControl: @Composable () -> Unit = {
                            StemRadiusSlider(
                                fraction = radiusFraction,
                                onFractionChange = { fraction ->
                                    val id = activeId ?: return@StemRadiusSlider
                                    val newRadius = minRadiusPx + fraction * (maxRadiusCapPx - minRadiusPx)
                                    updateRegion(id) { it.copy(radiusPx = newRadius) }
                                },
                                regionKey = regionId,
                            )
                        }
                        val moveHandle: @Composable () -> Unit = {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .height(36.dp)
                                    .pointerInput(regionId) {
                                        detectDragGestures(
                                            onDragEnd = { maybeFlipStem(regionId) },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            updateRegion(regionId) {
                                                it.copy(centerX = it.centerX + dragAmount.x, centerY = it.centerY + dragAmount.y)
                                            }
                                        }
                                    }
                                    .semantics { contentDescription = "按住拖动移动区域" },
                            ) {
                                Box(
                                    Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "✥ 移动",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        if (activeSide > 0) {
                            sliderControl()
                            moveHandle()
                        } else {
                            moveHandle()
                            sliderControl()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StemRadiusSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    regionKey: String,
) {
    val latestOnChange by rememberUpdatedState(onFractionChange)
    val density = LocalDensity.current.density
    val trackPadPx = 9f * density
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val thumbColor = Color(0xFFFFD166)

    fun updateFromX(x: Float, widthPx: Int) {
        val span = (widthPx - 2f * trackPadPx).coerceAtLeast(1f)
        latestOnChange(((x - trackPadPx) / span).coerceIn(0f, 1f))
    }

    Canvas(
        Modifier
            .width(88.dp)
            .height(36.dp)
            .pointerInput(regionKey) {
                detectTapGestures { offset -> updateFromX(offset.x, size.width) }
            }
            .pointerInput(regionKey) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x, size.width)
                }
            }
            .semantics { contentDescription = "滑动调节圆半径" },
    ) {
        val span = (size.width - 2f * trackPadPx).coerceAtLeast(1f)
        val cy = size.height / 2f
        val startX = trackPadPx
        val endX = trackPadPx + span
        val thumbX = startX + span * fraction.coerceIn(0f, 1f)
        drawLine(trackColor, Offset(startX, cy), Offset(endX, cy), strokeWidth = 4f * density, cap = StrokeCap.Round)
        drawLine(activeColor, Offset(startX, cy), Offset(thumbX, cy), strokeWidth = 4f * density, cap = StrokeCap.Round)
        drawCircle(thumbColor, 8f * density, Offset(thumbX, cy))
        drawCircle(Color(0xFF101D2D), 3f * density, Offset(thumbX, cy))
    }
}
