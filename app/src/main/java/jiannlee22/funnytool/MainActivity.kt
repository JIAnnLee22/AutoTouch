package jiannlee22.funnytool

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FunnyTheme { FunnyScreen() } }
    }
}

@Composable
fun FunnyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF5BE6BC), onPrimary = Color(0xFF002C22),
        secondary = Color(0xFFFFB49E), background = Color(0xFF101D2D),
        surface = Color(0xFF142335), surfaceContainer = Color(0xFF1B2B3E),
        surfaceContainerHigh = Color(0xFF24364B), onBackground = Color(0xFFE4EDF5),
        onSurface = Color(0xFFE4EDF5), onSurfaceVariant = Color(0xFFAFBAC8),
    ), content = content)
}

@Composable
private fun FunnyScreen(model: MainViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<ClickRegion?>(null) }
    var disclosure by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ClickRegion?>(null) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val config = state.settings.config
    val ready = state.settings.loaded && state.settings.error == null
    val editable = ready && state.runtime.mode == RunMode.IDLE && !state.saving

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_brand), contentDescription = null,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("FunnyTool", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("多区域 · 随机触摸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(when (state.runtime.mode) {
                                RunMode.IDLE -> "●  就绪面板"
                                RunMode.RUNNING -> "●  正在运行"
                                RunMode.PAUSED -> "●  已暂停"
                                RunMode.EDITING -> "●  悬浮编辑中"
                            }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("${state.runtime.completed} 次完成", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(state.runtime.message, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.runtime.mode == RunMode.RUNNING) {
                                Button(onClick = model::pause, modifier = Modifier.weight(1f)) { Text("暂停") }
                            } else {
                                Button(onClick = model::start, enabled = ready && !state.saving && state.runtime.connected &&
                                    state.runtime.mode != RunMode.EDITING && config.regions.any { it.enabled },
                                    modifier = Modifier.weight(1f)) {
                                    Text(if (state.runtime.mode == RunMode.PAUSED) "恢复运行" else "开始运行")
                                }
                            }
                            OutlinedButton(onClick = model::stop, enabled = state.runtime.mode != RunMode.IDLE,
                                modifier = Modifier.weight(1f)) { Text("停止") }
                        }
                        Text("首次先等待配置延迟；暂停/停止清空事件。手势完成不等于目标应用业务成功。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (state.runtime.connected) "✓  无障碍手势已连接" else "①  启用无障碍手势",
                            fontWeight = FontWeight.SemiBold)
                        Text("仅按你配置的规则点击，不读取页面内容。悬浮编辑随无障碍授权提供，无需额外悬浮窗权限。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { disclosure = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.runtime.connected) "管理系统授权" else "前往系统授权")
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("触摸区域  ${config.regions.size}", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { model.add(context.screenSpace()) }, enabled = editable) { Text("＋ 添加") }
                }
            }
            item {
                OutlinedButton(onClick = model::edit, enabled = editable && state.runtime.connected && config.regions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()) { Text("② 悬浮编辑 · 拖动圆心 / 圆边") }
            }
            state.settings.error?.let { message -> item { ErrorCard(message) } }
            if (!state.settings.loaded) item { Text("正在读取配置…") }
            if (ready && config.regions.isEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("从一个圆开始", style = MaterialTheme.typography.titleMedium)
                        Text("添加区域 → 编辑时间 → 在目标应用上悬浮调整 → 保存 → 开始。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(config.regions, key = { it.id }) { region ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(region.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(region.sourceRegionId?.let { id -> "依赖 ${config.regions.find { it.id == id }?.name ?: "未知区域"} 完成" }
                                    ?: "独立循环", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = region.enabled, enabled = editable,
                                onCheckedChange = { model.save(region.copy(enabled = it)) })
                        }
                        Text("${region.timing.baseDelayMs} + 随机 ${region.timing.randomDelayMinMs}~${region.timing.randomDelayMaxMs} ms",
                            style = MaterialTheme.typography.bodyLarge)
                        Text("圆心 (${region.centerX.toInt()}, ${region.centerY.toInt()}) · 半径 ${region.radiusPx.toInt()} px · 按压 ${region.pressDurationMs} ms",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (region.sourceRegionId != null) Text("重复事件：${region.repeatPolicy.title}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { selected = region }, enabled = editable) { Text("编辑参数") }
                            TextButton(onClick = { deleting = region }, enabled = editable,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                        }
                    }
                }
            }
            item {
                Text("首版默认 · 圆内面积均匀随机 / 单上游依赖 / 串行手势\n运行时可切换应用，使用悬浮条随时暂停或停止。熄屏、锁屏或屏幕边界变化后不自动恢复。\n请勿用于绕过安全限制或未经授权的操作。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("jiannlee22.funnytool · 0.1.0", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false },
        title = { Text("启用前请了解") },
        text = { Text("FunnyTool 将通过无障碍服务，在你主动开始后模拟跨应用点击，并显示区域编辑与停止工具条。它不读取页面内容，不上传数据，也不会自动开启或恢复点击。\n\n系统可能要求允许受限设置；仅在你信任此安装包时操作。已提交的一次按压可能在停止后完成，最长为你配置的按压时长（上限 200 ms）。") },
        confirmButton = { TextButton(onClick = {
            disclosure = false
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .onFailure { settingsError = "无法打开系统无障碍设置，请手动进入系统设置开启：${it.message}" }
        }) { Text("了解，打开设置") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("取消") } })
    selected?.let { region ->
        RegionDialog(region, config, onDismiss = { selected = null }, onSave = {
            model.save(it); selected = null
        })
    }
    deleting?.let { region -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text("删除 ${region.name}？") }, text = { Text("删除将立即保存。如果其他区域依赖它，需要先解除依赖。") },
        confirmButton = { TextButton(onClick = { model.delete(region.id); deleting = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    (state.error ?: settingsError)?.let { message -> AlertDialog(onDismissRequest = {
        model.clearError(); settingsError = null
    }, title = { Text("请检查") }, text = { Text(message) }, confirmButton = {
        TextButton(onClick = { model.clearError(); settingsError = null }) { Text("知道了") }
    }) }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun RegionDialog(region: ClickRegion, config: AppConfig, onDismiss: () -> Unit, onSave: (ClickRegion) -> Unit) {
    var name by remember { mutableStateOf(region.name) }
    var x by remember { mutableStateOf(region.centerX.toString()) }
    var y by remember { mutableStateOf(region.centerY.toString()) }
    var radius by remember { mutableStateOf(region.radiusPx.toString()) }
    var base by remember { mutableStateOf(region.timing.baseDelayMs.toString()) }
    var min by remember { mutableStateOf(region.timing.randomDelayMinMs.toString()) }
    var max by remember { mutableStateOf(region.timing.randomDelayMaxMs.toString()) }
    var press by remember { mutableStateOf(region.pressDurationMs.toString()) }
    var source by remember { mutableStateOf(region.sourceRegionId) }
    var policy by remember { mutableStateOf(region.repeatPolicy) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑区域") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("区域名称") }, singleLine = true)
            Text("触发方式", style = MaterialTheme.typography.labelLarge)
            ChoiceButton(label = source?.let { id -> "依赖 ${config.regions.find { it.id == id }?.name}" } ?: "独立循环",
                options = listOf<String?>(null) + config.regions.filter { it.id != region.id }.map { it.id },
                title = { id -> id?.let { selected -> "依赖 ${config.regions.find { it.id == selected }?.name} 完成" } ?: "独立循环" },
                onSelect = { source = it })
            if (source != null) ChoiceButton(policy.title, RepeatPolicy.entries, { it.title }) { policy = it }
            Text(if (source == null) "每次手势完成后等待以下时间" else "上游手势完成后，等待以下时间点击一次",
                style = MaterialTheme.typography.bodySmall)
            NumberField("基础延迟 (ms)", base) { base = it }
            NumberField("随机附加下限 (ms)", min) { min = it }
            NumberField("随机附加上限 (ms)", max) { max = it }
            NumberField("短按时长 (ms，1~200)", press) { press = it }
            HorizontalDivider()
            Text("屏幕物理坐标（px）· 圆形须完整位于安全边界内", style = MaterialTheme.typography.bodySmall)
            NumberField("圆心 X (px)", x, decimal = true) { x = it }
            NumberField("圆心 Y (px)", y, decimal = true) { y = it }
            NumberField("半径 (px)", radius, decimal = true) { radius = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        fun parseMs(text: String): Long? = text.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.toLongOrNull()
        val baseMs = parseMs(base); val minMs = parseMs(min); val maxMs = parseMs(max); val pressMs = parseMs(press)
        val centerX = x.toFloatOrNull(); val centerY = y.toFloatOrNull(); val radiusPx = radius.toFloatOrNull()
        if (baseMs == null || minMs == null || maxMs == null || pressMs == null ||
            centerX == null || centerY == null || radiusPx == null) {
            error = "请输入有效数字；时间必须为非负整数 ms，且不能超出 Long 范围"
        } else {
            val updated = region.copy(name = name.trim(), centerX = centerX, centerY = centerY, radiusPx = radiusPx,
                timing = TimingRule(baseMs, minMs, maxMs), pressDurationMs = pressMs, sourceRegionId = source, repeatPolicy = policy)
            val message = config.copy(regions = config.regions.map { if (it.id == region.id) updated else it }).error()
            if (message != null) error = message else onSave(updated)
        }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun NumberField(label: String, value: String, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun <T> ChoiceButton(label: String, options: List<T>, title: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(title(option)) }, onClick = {
                onSelect(option); expanded = false
            }) }
        }
    }
}
