package jiannlee22.funnytool

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabledInSettings by remember { mutableStateOf(context.isAccessibilityEnabledInSettings()) }
    var ignoringBattery by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    DisposableEffect(lifecycleOwner, state.runtime.connected) {
        fun refreshSystemState() {
            enabledInSettings = context.isAccessibilityEnabledInSettings()
            ignoringBattery = context.isIgnoringBatteryOptimizations()
        }
        refreshSystemState()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var selected by remember { mutableStateOf<ClickRegion?>(null) }
    var disclosure by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ClickRegion?>(null) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var groupAction by remember { mutableStateOf<String?>(null) }
    var groupName by remember { mutableStateOf("") }
    var triggerInput by remember(state.runtime.targetTriggerCount) { mutableStateOf(state.runtime.targetTriggerCount.toString()) }
    val triggerCountValid = triggerInput.isNotEmpty() && triggerInput.all(Char::isDigit) &&
        triggerInput.toLongOrNull()?.let { it > 0 } == true
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
                            Text("${state.runtime.completed}/${state.runtime.targetTriggerCount} 次组触发", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            if (!state.runtime.connected && enabledInSettings && state.runtime.message == "请先开启无障碍服务") {
                                "系统无障碍开关已开启，等待服务连接（若长期未连接请重开开关并允许后台无限制）"
                            } else state.runtime.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(value = triggerInput, onValueChange = { value ->
                            triggerInput = value
                            if (value.isNotEmpty() && value.all(Char::isDigit)) value.toLongOrNull()?.let { model.setTarget(it) }
                        }, label = { Text("本次组触发次数（完成后停止，不保存累计）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                            enabled = editable, isError = !triggerCountValid, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.runtime.mode == RunMode.RUNNING) {
                                Button(onClick = model::pause, modifier = Modifier.weight(1f)) { Text("暂停") }
                            } else {
                                Button(onClick = model::start, enabled = ready && !state.saving && state.runtime.connected &&
                                    state.runtime.mode != RunMode.EDITING && config.regions.any { it.enabled } && triggerCountValid,
                                    modifier = Modifier.weight(1f)) {
                                    Text(if (state.runtime.mode == RunMode.PAUSED) "恢复运行" else "开始运行")
                                }
                            }
                            OutlinedButton(onClick = model::stop, enabled = state.runtime.mode != RunMode.IDLE,
                                modifier = Modifier.weight(1f)) { Text("停止") }
                        }
                        Text("每次组触发完成各区域的连点批次；暂停保留本次进度，停止后重新开始清零。手势完成不等于目标应用业务成功。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when {
                            state.runtime.connected -> "✓  无障碍手势已连接"
                            enabledInSettings -> "⚠  无障碍开关已开启，但服务未连接"
                            else -> "①  启用无障碍手势"
                        }, fontWeight = FontWeight.SemiBold)
                        Text(if (!state.runtime.connected && enabledInSettings) {
                            "系统开关仍为开启，但服务进程可能被后台清理或正在重连。若数秒后仍未连接，请进入无障碍设置关闭后重新开启，并设置后台无限制。"
                        } else {
                            "仅按你配置的规则点击，不读取页面内容。悬浮编辑随无障碍授权提供，无需额外悬浮窗权限。"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { disclosure = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(when {
                                state.runtime.connected -> "管理系统授权"
                                enabledInSettings -> "重新打开无障碍设置"
                                else -> "前往系统授权"
                            })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(if (ignoringBattery) "✓  已忽略电池优化（后台不易被系统清理）" else "⚠  未忽略电池优化（后台清理可能关闭无障碍）",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ignoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        Text("原生系统普通杀进程会自动重连无障碍；但小米/OPPO/vivo 等系统若未开启「省电无限制 + 自启动 + 最近任务锁定」，划掉后台或强行停止会直接关闭无障碍开关。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!ignoringBattery) {
                                FilledTonalButton(onClick = {
                                    val packageUri = Uri.parse("package:${context.packageName}")
                                    val opened = runCatching {
                                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
                                    }.isSuccess || runCatching {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }.isSuccess || runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
                                    }.isSuccess
                                    if (!opened) settingsError = "无法打开电池优化设置，请在系统设置中将 FunnyTool 设为无限制"
                                }, modifier = Modifier.weight(1f)) { Text("允许后台无限制") }
                            }
                            OutlinedButton(onClick = {
                                val packageUri = Uri.parse("package:${context.packageName}")
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
                                }.onFailure { settingsError = "无法打开应用详情设置：${it.message}" }
                            }, modifier = Modifier.weight(1f)) { Text("应用详情 / 自启动") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("单选开启的组", style = MaterialTheme.typography.titleMedium)
                        config.groups.forEach { group ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = group.id == config.selectedGroupId, enabled = editable,
                                    onClick = { selected = null; deleting = null; model.selectGroup(group.id) })
                                Text("${group.name} · ${group.regions.size} 个区域", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Row {
                            TextButton(onClick = { groupName = "组 ${config.groups.size + 1}"; groupAction = "add" }, enabled = editable) { Text("新建组") }
                            TextButton(onClick = { groupName = config.selectedGroup.name; groupAction = "rename" }, enabled = editable) { Text("改组名") }
                            TextButton(onClick = model::deleteGroup, enabled = editable && config.groups.size > 1 && config.regions.isEmpty()) { Text("删除空组") }
                        }
                        Text("仅运行和编辑选定组；运行或暂停期间请先停止再切组。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${config.selectedGroup.name} · ${config.regions.size} 个区域", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    FilledTonalButton(onClick = { model.add(context.screenSpace()) }, enabled = editable) { Text("＋ 添加") }
                }
            }
            item {
                OutlinedButton(onClick = model::edit, enabled = editable && state.runtime.connected && config.regions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()) { Text("② 悬浮编辑 · 滑块缩放 / 拖动移动") }
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
                                    ?: "独立计时", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = region.enabled, enabled = editable,
                                onCheckedChange = { model.save(region.copy(enabled = it)) })
                        }
                        Text("首次等待 ${region.timing.baseDelayMs} + 随机 ${region.timing.randomDelayMinMs}~${region.timing.randomDelayMaxMs} ms",
                            style = MaterialTheme.typography.bodyLarge)
                        Text("每次触发 ${region.clicksPerTrigger} 次点击 · 后续间隔 ${region.clickIntervalMinMs}~${region.clickIntervalMaxMs} ms",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("圆心 (${region.centerX.toInt()}, ${region.centerY.toInt()}) · 半径 ${region.radiusPx.toInt()} px · 短按 ${region.pressDurationMinMs}~${region.pressDurationMaxMs} ms",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (region.sourceRegionId != null) Text("同组上游整批完成后计时，当前区域整批完成才激活下游",
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
                Text("${BuildConfig.APPLICATION_ID} · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
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
        RegionDialog(region, config, onDismiss = { selected = null }, onSave = { updated, targetGroup ->
            model.save(updated, targetGroup); selected = null
        })
    }
    groupAction?.let { action -> AlertDialog(onDismissRequest = { groupAction = null },
        title = { Text(if (action == "add") "新建组" else "修改组名") },
        text = { OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("组名") }, singleLine = true) },
        confirmButton = { TextButton(enabled = groupName.isNotBlank(), onClick = {
            if (action == "add") model.addGroup(groupName) else model.renameGroup(groupName)
            groupAction = null
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = { groupAction = null }) { Text("取消") } }) }
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
private fun RegionDialog(region: ClickRegion, config: AppConfig, onDismiss: () -> Unit, onSave: (ClickRegion, String) -> Unit) {
    var name by remember { mutableStateOf(region.name) }
    var x by remember { mutableStateOf(region.centerX.toString()) }
    var y by remember { mutableStateOf(region.centerY.toString()) }
    var radius by remember { mutableStateOf(region.radiusPx.toString()) }
    var base by remember { mutableStateOf(region.timing.baseDelayMs.toString()) }
    var min by remember { mutableStateOf(region.timing.randomDelayMinMs.toString()) }
    var max by remember { mutableStateOf(region.timing.randomDelayMaxMs.toString()) }
    var pressMin by remember { mutableStateOf(region.pressDurationMinMs.toString()) }
    var pressMax by remember { mutableStateOf(region.pressDurationMaxMs.toString()) }
    var clickCount by remember { mutableStateOf(region.clicksPerTrigger.toString()) }
    var intervalMin by remember { mutableStateOf(region.clickIntervalMinMs.toString()) }
    var intervalMax by remember { mutableStateOf(region.clickIntervalMaxMs.toString()) }
    var targetGroup by remember { mutableStateOf(config.selectedGroupId) }
    var source by remember { mutableStateOf(region.sourceRegionId) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑区域") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("区域名称") }, singleLine = true)
            ChoiceButton("所属组：${config.groups.first { it.id == targetGroup }.name}", config.groups.map { it.id },
                { id -> config.groups.first { it.id == id }.name }) { targetGroup = it }
            Text("跨组移动需先解除此区域的相关依赖；保存后只在目标组中显示。", style = MaterialTheme.typography.bodySmall)
            Text("触发方式", style = MaterialTheme.typography.labelLarge)
            ChoiceButton(label = source?.let { id -> "依赖 ${config.regions.find { it.id == id }?.name}" } ?: "独立计时",
                options = listOf<String?>(null) + config.regions.filter { it.id != region.id }.map { it.id },
                title = { id -> id?.let { selected -> "依赖 ${config.regions.find { it.id == selected }?.name} 完成" } ?: "独立计时" },
                onSelect = { source = it })
            Text(if (source == null) "每次组触发开始后，先等待以下时间再开始本区域连点" else "上游整批连点完成后，先等待以下时间再开始本区域连点",
                style = MaterialTheme.typography.bodySmall)
            NumberField("首次基础延迟 (ms)", base) { base = it }
            NumberField("首次随机附加下限 (ms)", min) { min = it }
            NumberField("首次随机附加上限 (ms)", max) { max = it }
            Text("基础延迟 + 随机附加只用于本批第 1 次点击。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            NumberField("单次触发点击次数 (至少 1 次)", clickCount) { clickCount = it }
            NumberField("连点间隔下限 (ms)", intervalMin) { intervalMin = it }
            NumberField("连点间隔上限 (ms)", intervalMax) { intervalMax = it }
            Text("第 2 次起，每次只抽取上述区间的间隔，不加基础延迟；从上次手势完成后计时。每次重新随机落点与按压，整批完成才激活下游。", style = MaterialTheme.typography.bodySmall)
            NumberField("短按时长下限 (ms，1~200)", pressMin) { pressMin = it }
            NumberField("短按时长上限 (ms，1~200)", pressMax) { pressMax = it }
            Text("每次点击独立随机抽取，包含上下限；两者相同为固定短按。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("屏幕物理坐标（px）· 圆形须完整位于安全边界内", style = MaterialTheme.typography.bodySmall)
            NumberField("圆心 X (px)", x, decimal = true) { x = it }
            NumberField("圆心 Y (px)", y, decimal = true) { y = it }
            NumberField("半径 (px)", radius, decimal = true) { radius = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        fun parseNumber(text: String): Long? = text.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.toLongOrNull()
        val baseMs = parseNumber(base); val minMs = parseNumber(min); val maxMs = parseNumber(max)
        val pressMinMs = parseNumber(pressMin); val pressMaxMs = parseNumber(pressMax)
        val clicks = parseNumber(clickCount); val intervalMinMs = parseNumber(intervalMin); val intervalMaxMs = parseNumber(intervalMax)
        val centerX = x.toFloatOrNull(); val centerY = y.toFloatOrNull(); val radiusPx = radius.toFloatOrNull()
        if (baseMs == null || minMs == null || maxMs == null || pressMinMs == null || pressMaxMs == null ||
            clicks == null || intervalMinMs == null || intervalMaxMs == null || centerX == null || centerY == null || radiusPx == null) {
            error = "请输入有效数字；时间和次数必须为非负整数，且不能超出 Long 范围"
        } else {
            val updated = region.copy(name = name.trim(), centerX = centerX, centerY = centerY, radiusPx = radiusPx,
                timing = TimingRule(baseMs, minMs, maxMs), pressDurationMinMs = pressMinMs, pressDurationMaxMs = pressMaxMs,
                sourceRegionId = source, clicksPerTrigger = clicks, clickIntervalMinMs = intervalMinMs, clickIntervalMaxMs = intervalMaxMs)
            val message = config.updateSelected { group -> group.copy(regions = group.regions.map { if (it.id == region.id) updated else it }) }.error()
            if (message != null) error = message else onSave(updated, targetGroup)
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
