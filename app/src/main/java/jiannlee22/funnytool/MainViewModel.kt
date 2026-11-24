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
    val targetTriggerCount: Long = 1,
    val completedRegionIds: Set<String> = emptySet(),
    val completedClicksByRegion: Map<String, Long> = emptyMap(),
    val runGroupId: String? = null,
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
        if (TapAccessibilityService.state.value.mode != RunMode.IDLE || saving.value ||
            TapAccessibilityService.instance?.isBusy == true) return
        saving.value = true
        viewModelScope.launch {
            try { store.update(transform); TapAccessibilityService.resetProgress(); error.value = null }
            catch (e: Exception) { error.value = e.message ?: "保存失败" }
            finally { saving.value = false }
        }
    }

    fun add(space: ScreenSpace) = update { config ->
        require(config.screen == null || config.screen == space || config.regions.isEmpty()) {
            "当前组屏幕已变化，请先通过悬浮编辑重新确认坐标"
        }
        val region = ClickRegion(name = "区域 ${config.regions.size + 1}",
            centerX = (space.leftPx + space.rightPx) / 2f, centerY = (space.topPx + space.bottomPx) / 2f).fitted(space)
        config.updateSelected { it.copy(regions = it.regions + region, screen = space) }
    }

    fun save(region: ClickRegion, targetGroupId: String? = null) = update { config ->
        require(config.regions.any { it.id == region.id }) { "仅能编辑选定组的区域" }
        if (targetGroupId == null || targetGroupId == config.selectedGroupId) {
            config.updateSelected { group -> group.copy(regions = group.regions.map { if (it.id == region.id) region else it }) }
        } else {
            require(region.sourceRegionId == null && config.regions.none { it.sourceRegionId == region.id }) {
                "移动分组前请先解除此区域的上游和下游依赖"
            }
            val target = config.groups.first { it.id == targetGroupId }
            val space = target.screen ?: requireNotNull(config.screen)
            config.copy(groups = config.groups.map { group -> when (group.id) {
                config.selectedGroupId -> group.copy(regions = group.regions.filterNot { it.id == region.id })
                targetGroupId -> group.copy(regions = group.regions + region.fitted(space), screen = space)
                else -> group
            } })
        }
    }

    fun delete(id: String) = update { config ->
        require(config.regions.none { it.sourceRegionId == id }) { "请先解除其他区域对此区域的依赖" }
        config.updateSelected { it.copy(regions = it.regions.filterNot { r -> r.id == id }) }
    }

    fun addGroup(name: String) = update { config ->
        val group = RegionGroup(name = name.trim())
        config.copy(groups = config.groups + group, selectedGroupId = group.id)
    }
    fun selectGroup(id: String) = update { it.copy(selectedGroupId = id) }
    fun renameGroup(name: String) = update { it.updateSelected { group -> group.copy(name = name.trim()) } }
    fun deleteGroup() = update { config ->
        require(config.groups.size > 1) { "至少保留一个组" }
        require(config.regions.isEmpty()) { "请先移动或删除组内区域，避免误删配置" }
        val remaining = config.groups.filterNot { it.id == config.selectedGroupId }
        config.copy(groups = remaining, selectedGroupId = remaining.first().id)
    }

    fun clearError() { error.value = null }
    fun setTarget(count: Long) { TapAccessibilityService.setTargetTriggerCount(count) }
    fun start() { withService { startRun() } }
    fun edit() { withService { editRegions() } }
    fun openToolbar() { withService { openToolbar() } }
    fun stop() { TapAccessibilityService.instance?.stop("已停止，本次待执行任务已清空") }
    fun pause() { withService { pause() } }

    private fun withService(action: TapAccessibilityService.() -> Unit) {
        TapAccessibilityService.instance?.action() ?: run {
            val app = getApplication<Application>()
            error.value = if (app.isAccessibilityEnabledInSettings()) {
                "系统无障碍开关已开启，但服务尚未连接（可能被系统后台限制或正在重连）。请稍候重试，或到无障碍设置关闭后重新开启，并允许后台无限制/自启动。"
            } else {
                "请先在系统设置中启用 FunnyTool 无障碍服务"
            }
        }
    }
}
