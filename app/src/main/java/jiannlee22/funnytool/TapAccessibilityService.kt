package jiannlee22.funnytool

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

@Suppress("DEPRECATION")
@SuppressLint("DiscouragedApi") // Android 26-29 fallback: system bar resource IDs are not public.
fun Context.screenSpace(): ScreenSpace {
    val manager = getSystemService(WindowManager::class.java)
    if (Build.VERSION.SDK_INT >= 30) {
        val metrics = manager.maximumWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        return ScreenSpace(bounds.width(), bounds.height(), insets.left, insets.top,
            bounds.width() - insets.right, bounds.height() - insets.bottom)
    }
    val metrics = android.util.DisplayMetrics()
    manager.defaultDisplay.getRealMetrics(metrics)
    fun dimension(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id == 0) 0 else resources.getDimensionPixelSize(id)
    }
    val landscape = metrics.widthPixels > metrics.heightPixels
    val nav = dimension(if (landscape) "navigation_bar_width" else "navigation_bar_height")
    // Conservative rectangle on old devices, also excluding likely display-cutout areas.
    val status = dimension("status_bar_height")
    return ScreenSpace(metrics.widthPixels, metrics.heightPixels,
        if (landscape) status else 0, status,
        metrics.widthPixels - if (landscape) maxOf(nav, status) else 0,
        metrics.heightPixels - if (landscape) status else nav)
}

class TapAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {
    companion object {
        private val mutableState = MutableStateFlow(RuntimeState())
        val state = mutableState.asStateFlow()
        var instance: TapAccessibilityService? = null
            private set
    }

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store get() = (application as FunnyToolApp).store
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private var toolbar: ComposeView? = null
    private var editor: ComposeView? = null
    private var editingConfig by mutableStateOf<AppConfig?>(null)
    private var capturedScreen: ScreenSpace? = null
    private var inFlight = false
    private var connectionGeneration = 0L
    private var saving = false
    private var receiverRegistered = false
    private var displayRegistered = false
    private var toolbarParams: WindowManager.LayoutParams? = null
    private val scheduler = ClickScheduler(scope, ::tap, { region ->
        mutableState.value = state.value.copy(completed = state.value.completed + 1, message = "${region.name} 手势完成")
    }, { stop(it) })

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stop("屏幕已关闭，已停止") }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) { stop("显示设备已变化，已停止") }
        override fun onDisplayChanged(displayId: Int) {
            if (capturedScreen != null && capturedScreen != screenSpace()) stop("屏幕边界已变化，请重新编辑区域")
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        stop()
        connectionGeneration++
        inFlight = false
        instance = this
        registry.currentState = Lifecycle.State.RESUMED
        mutableState.value = RuntimeState(connected = true, message = "服务已连接，等待主动开始")
        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            receiverRegistered = true
        }
        if (!displayRegistered) {
            displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            displayRegistered = true
        }
    }

    fun startRun() {
        if (saving) return
        if (state.value.mode == RunMode.EDITING) { report("请先保存或取消悬浮编辑"); return }
        if (inFlight) { report("上次手势尚未结束；若持续不恢复，请关闭后重新开启无障碍服务"); return }
        val settings = store.state.value
        if (!settings.loaded || settings.error != null) { report(settings.error ?: "配置加载中"); return }
        val space = screenSpace()
        settings.config.runError(space)?.let { report(it); return }
        if (!unlocked()) { report("请解锁并保持屏幕亮起"); return }
        scheduler.stop()
        try {
            capturedScreen = space
            showToolbar(space)
            mutableState.value = state.value.copy(mode = RunMode.RUNNING, message = "运行中 · 完成后重新计时",
                completed = if (state.value.mode == RunMode.PAUSED) state.value.completed else 0)
            scheduler.start(settings.config)
        } catch (e: Exception) { stop("无法显示停止工具条：${e.message}") }
    }

    fun pause() {
        if (state.value.mode != RunMode.RUNNING) return
        scheduler.stop()
        mutableState.value = state.value.copy(mode = RunMode.PAUSED, message = "已暂停，恢复时重新计时")
    }

    fun stop(message: String = "已停止") {
        scheduler.stop()
        removeEditor()
        toolbar?.let { runCatching { windows.removeViewImmediate(it) } }
        toolbar = null
        toolbarParams = null
        capturedScreen = null
        mutableState.value = state.value.copy(mode = RunMode.IDLE, message = message)
    }

    private fun report(message: String) { mutableState.value = state.value.copy(message = message) }

    fun editRegions() {
        if (saving || inFlight) { report("请等待保存或手势完成"); return }
        val settings = store.state.value
        if (!settings.loaded || settings.error != null) { report(settings.error ?: "配置加载中"); return }
        if (settings.config.regions.isEmpty()) { report("请先在应用中添加区域"); return }
        stop()
        val space = screenSpace()
        if (!space.valid()) { report("屏幕安全边界不可用"); return }
        capturedScreen = space
        editingConfig = settings.config.copy(screen = space, regions = settings.config.regions.map { it.fitted(space) })
        mutableState.value = state.value.copy(mode = RunMode.EDITING, message = "拖动圆心移动 · 拖动圆边缩放")
        try {
            editor = composeWindow().apply {
                setContent {
                    FunnyTheme {
                        editingConfig?.let { config ->
                            RegionEditor(config) { editingConfig = it }
                        }
                    }
                }
            }
            windows.addView(editor, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
            showToolbar(space)
        } catch (e: Exception) { stop("悬浮编辑无法打开：${e.message}") }
    }

    fun saveEditor() {
        val config = editingConfig ?: return
        if (saving) return
        if (config.screen != screenSpace()) { stop("屏幕已变化，未保存编辑"); return }
        saving = true
        scope.launch {
            try {
                store.update { config }
                // Keep the control bar on the target app, but never start automatically.
                if (state.value.mode == RunMode.EDITING) {
                    removeEditor()
                    mutableState.value = state.value.copy(mode = RunMode.PAUSED, message = "区域已保存，点击恢复后开始计时")
                }
            } catch (e: Exception) { report("保存失败：${e.message}") }
            finally { saving = false }
        }
    }

    private fun removeEditor() {
        editor?.let { runCatching { windows.removeViewImmediate(it) } }
        editor = null
        editingConfig = null
    }

    private fun composeWindow() = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@TapAccessibilityService)
        setViewTreeSavedStateRegistryOwner(this@TapAccessibilityService)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    @SuppressLint("RtlHardcoded") // Physical screen coordinates must use a fixed left origin.
    private fun params(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.LEFT }

    private fun showToolbar(space: ScreenSpace) {
        if (toolbar != null) return
        val layout = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        layout.x = space.leftPx
        layout.y = space.topPx
        toolbarParams = layout
        toolbar = composeWindow().apply {
            setContent { FunnyTheme { OverlayToolbar(this@TapAccessibilityService, ::moveToolbar) } }
        }
        windows.addView(toolbar, layout)
    }

    private fun moveToolbar(dx: Float, dy: Float) {
        val view = toolbar ?: return
        val layout = toolbarParams ?: return
        val space = capturedScreen ?: return
        layout.x = (layout.x + dx.toInt()).coerceIn(space.leftPx, maxOf(space.leftPx, space.rightPx - view.width))
        layout.y = (layout.y + dy.toInt()).coerceIn(space.topPx, maxOf(space.topPx, space.bottomPx - view.height))
        runCatching { windows.updateViewLayout(view, layout) }.onFailure { stop("工具条不可用，已停止") }
    }

    private fun unlocked(): Boolean = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private suspend fun tap(region: ClickRegion): Boolean {
        if (state.value.mode != RunMode.RUNNING || !unlocked() || capturedScreen != screenSpace() || inFlight) return false
        val view = toolbar ?: return false
        var layoutWaitMs = 0
        while ((view.width == 0 || view.height == 0) && layoutWaitMs < 1000) {
            delay(16)
            layoutWaitMs += 16
        }
        if (!view.isShown || view.width == 0 || view.height == 0 ||
            state.value.mode != RunMode.RUNNING || !unlocked() || capturedScreen != screenSpace()) return false
        val location = IntArray(2).also(view::getLocationOnScreen)
        val blocked = Rect(location[0] - 12, location[1] - 12, location[0] + view.width + 12, location[1] + view.height + 12)
        val point = (1..128).asSequence().map { region.randomPoint() }
            .firstOrNull { !blocked.contains(it.first.toInt(), it.second.toInt()) } ?: return false
        val path = Path().apply { moveTo(point.first, point.second) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, region.pressDurationMs),
        ).build()
        // Cancellation does not clear inFlight: a submitted Android gesture may finish later.
        // Until its callback arrives, restart is refused, avoiding concurrent gesture cancellation.
        val generation = connectionGeneration
        return withTimeout(region.pressDurationMs + 5000) {
            suspendCancellableCoroutine { continuation ->
                inFlight = true
                fun finish(success: Boolean) {
                    if (generation != connectionGeneration) return
                    inFlight = false
                    if (continuation.isActive) continuation.resume(success)
                }
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { finish(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { finish(false) }
                }
                try {
                    if (!dispatchGesture(gesture, callback, null)) {
                        inFlight = false
                        if (continuation.isActive) continuation.resume(false)
                    }
                } catch (e: Exception) {
                    inFlight = false
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { stop("无障碍服务中断，已停止") }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        stop("屏幕配置变化，已停止；请重新确认区域")
    }
    override fun onUnbind(intent: Intent?): Boolean {
        stop("无障碍服务已断开")
        connectionGeneration++
        if (instance === this) instance = null
        mutableState.value = RuntimeState(message = "服务已断开，请重新启用")
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        stop("服务已关闭")
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        if (displayRegistered) displays.unregisterDisplayListener(displayListener)
        registry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        if (instance === this) instance = null
        mutableState.value = RuntimeState(message = "服务已关闭")
        super.onDestroy()
    }
}
