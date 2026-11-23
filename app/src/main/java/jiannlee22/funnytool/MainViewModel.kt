package jiannlee22.funnytool

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RunMode { IDLE, EDITING, RUNNING, PAUSED }

data class RuntimeState(
    val connected: Boolean = false,
    val mode: RunMode = RunMode.IDLE,
    val message: String = "请先开启无障碍服务",
    val completed: Long = 0,
)

data class UiState(
    val settings: StoredSettings = StoredSettings(),
    val runtime: RuntimeState = RuntimeState(),
    val error: String? = null,
    val saving: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as FunnyToolApp).store
    private val error = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)
    val state = combine(store.state, TapAccessibilityService.state, error, saving, ::UiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private fun update(transform: (AppConfig) -> AppConfig) {
        if (TapAccessibilityService.state.value.mode != RunMode.IDLE || saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { store.update(transform); error.value = null }
            catch (e: Exception) { error.value = e.message ?: "保存失败" }
            finally { saving.value = false }
        }
    }

    fun add(space: ScreenSpace) = update { config ->
        require(config.screen == null || config.screen == space || config.regions.isEmpty()) {
            "屏幕已变化，请先通过悬浮编辑重新确认坐标"
        }
        val region = ClickRegion(
            name = "区域 ${config.regions.size + 1}",
            centerX = (space.leftPx + space.rightPx) / 2f,
            centerY = (space.topPx + space.bottomPx) / 2f,
        ).fitted(space)
        config.copy(regions = config.regions + region, screen = space)
    }

    fun save(region: ClickRegion) = update { config ->
        config.copy(regions = config.regions.map { if (it.id == region.id) region else it })
    }

    fun delete(id: String) = update { config ->
        require(config.regions.none { it.sourceRegionId == id }) { "请先解除其他区域对此区域的依赖" }
        config.copy(regions = config.regions.filterNot { it.id == id })
    }

    fun clearError() { error.value = null }
    fun start() { withService { startRun() } }
    fun edit() { withService { editRegions() } }
    fun stop() { TapAccessibilityService.instance?.stop("已停止，待执行事件已清空") }
    fun pause() { withService { pause() } }

    private fun withService(action: TapAccessibilityService.() -> Unit) {
        TapAccessibilityService.instance?.action() ?: run { error.value = "请先在系统设置中启用 FunnyTool 无障碍服务" }
    }
}
