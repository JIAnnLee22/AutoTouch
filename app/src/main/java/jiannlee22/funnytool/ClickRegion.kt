package jiannlee22.funnytool

import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// All persisted time values and public timing parameters are milliseconds.
data class TimingRule(
    val baseDelayMs: Long = 1000,
    val randomDelayMinMs: Long = 0,
    val randomDelayMaxMs: Long = 0,
) {
    fun error(): String? = when {
        baseDelayMs < 0 || randomDelayMinMs < 0 || randomDelayMaxMs < 0 -> "时间必须是非负整数 ms"
        randomDelayMinMs > randomDelayMaxMs -> "随机下限不能大于上限"
        baseDelayMs > Long.MAX_VALUE - randomDelayMaxMs -> "基础时间与随机上限相加溢出"
        else -> null
    }

    fun sampleDelayMs(random: Random = Random.Default): Long {
        require(error() == null)
        return baseDelayMs + randomInclusive(randomDelayMinMs, randomDelayMaxMs, random)
    }
}

private fun randomInclusive(minMs: Long, maxMs: Long, random: Random): Long {
    require(minMs >= 0 && minMs <= maxMs)
    // Unsigned sampling avoids max + 1 overflow even for 0..Long.MAX_VALUE.
    val width = maxMs.toULong() - minMs.toULong() + 1uL
    val offset = if (width == 1uL) 0uL else random.nextULongBelow(width)
    return minMs + offset.toLong()
}

private fun Random.nextULongBelow(bound: ULong): ULong {
    // Rejection sampling removes modulo bias. Generated values cover 2^63 outcomes.
    val domain = 1uL shl 63
    val limit = domain - domain % bound
    var value: ULong
    do { value = nextLong().toULong() shr 1 } while (value >= limit)
    return value % bound
}

enum class RepeatPolicy(val title: String) {
    IGNORE("忽略新事件"), RESTART("按最新事件计时"), QUEUE("每次排队"),
}

data class ScreenSpace(
    val widthPx: Int,
    val heightPx: Int,
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
) {
    val usableWidth get() = rightPx - leftPx
    val usableHeight get() = bottomPx - topPx
    fun valid() = widthPx > 0 && heightPx > 0 && leftPx >= 0 && topPx >= 0 &&
        rightPx <= widthPx && bottomPx <= heightPx && usableWidth >= 2 && usableHeight >= 2
}

data class ClickRegion(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val centerX: Float,
    val centerY: Float,
    val radiusPx: Float = 60f,
    val enabled: Boolean = true,
    val timing: TimingRule = TimingRule(),
    // null means independent; dependents activate after the upstream's complete click batch.
    val sourceRegionId: String? = null,
    val repeatPolicy: RepeatPolicy = RepeatPolicy.IGNORE,
    val pressDurationMinMs: Long = 50,
    val pressDurationMaxMs: Long = 50,
    val clicksPerTrigger: Long = 1,
    val clickIntervalMinMs: Long = 100,
    val clickIntervalMaxMs: Long = 100,
) {
    fun pressDurationError(): String? = when {
        pressDurationMinMs !in 1..200 || pressDurationMaxMs !in 1..200 -> "短按上下限需为 1~200 ms"
        pressDurationMinMs > pressDurationMaxMs -> "短按下限不能大于上限"
        else -> null
    }

    fun samplePressDurationMs(random: Random = Random.Default): Long {
        require(pressDurationError() == null) { pressDurationError().orEmpty() }
        return randomInclusive(pressDurationMinMs, pressDurationMaxMs, random)
    }

    fun clickBatchError(): String? = when {
        clicksPerTrigger < 1 -> "单次触发点击次数必须为正整数"
        clickIntervalMinMs < 0 || clickIntervalMaxMs < 0 -> "连点间隔必须为非负整数 ms"
        clickIntervalMinMs > clickIntervalMaxMs -> "连点间隔下限不能大于上限"
        else -> null
    }

    fun sampleClickIntervalMs(random: Random = Random.Default): Long {
        require(clickBatchError() == null) { clickBatchError().orEmpty() }
        return randomInclusive(clickIntervalMinMs, clickIntervalMaxMs, random)
    }

    fun randomPoint(random: Random = Random.Default): Pair<Float, Float> {
        val theta = random.nextDouble() * 2 * PI
        val rho = radiusPx * sqrt(random.nextDouble())
        return (centerX + rho * cos(theta)).toFloat() to (centerY + rho * sin(theta)).toFloat()
    }

    fun fits(space: ScreenSpace): Boolean = centerX.isFinite() && centerY.isFinite() &&
        radiusPx.isFinite() && radiusPx >= 1f && centerX - radiusPx >= space.leftPx &&
        centerY - radiusPx >= space.topPx && centerX + radiusPx <= space.rightPx - 1 &&
        centerY + radiusPx <= space.bottomPx - 1

    fun fitted(space: ScreenSpace): ClickRegion {
        val radius = radiusPx.coerceIn(1f, (minOf(space.usableWidth, space.usableHeight) - 1) / 2f)
        return copy(
            radiusPx = radius,
            centerX = centerX.coerceIn(space.leftPx + radius, space.rightPx - 1 - radius),
            centerY = centerY.coerceIn(space.topPx + radius, space.bottomPx - 1 - radius),
        )
    }
}

const val DEFAULT_GROUP_ID = "default"

data class RegionGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val regions: List<ClickRegion> = emptyList(),
    val screen: ScreenSpace? = null,
) {
    fun error(): String? {
        if (regions.isEmpty()) return null
        val space = screen ?: return "请先编辑区域，确认屏幕坐标"
        if (!space.valid()) return "屏幕安全边界无效"
        if (regions.map { it.id }.distinct().size != regions.size) return "区域 ID 重复"
        val byId = regions.associateBy { it.id }
        for (region in regions) {
            if (region.id.isBlank() || region.name.isBlank()) return "区域名称与 ID 不能为空"
            region.timing.error()?.let { return "${region.name}：$it" }
            region.pressDurationError()?.let { return "${region.name}：$it" }
            region.clickBatchError()?.let { return "${region.name}：$it" }
            if (!region.fits(space)) return "${region.name}：圆形必须完整处于安全屏幕内"
            if (region.sourceRegionId != null && region.sourceRegionId !in byId) return "${region.name}：上游必须位于同一组"
            val visited = mutableSetOf<String>()
            var current: ClickRegion? = region
            while (current != null) {
                if (!visited.add(current.id)) return "不能创建循环依赖"
                current = current.sourceRegionId?.let(byId::get)
            }
        }
        return null
    }

    fun runError(space: ScreenSpace): String? {
        error()?.let { return it }
        if (screen != space) return "当前组屏幕尺寸或安全边界已变化，请重新编辑区域"
        val active = regions.filter { it.enabled }
        if (active.isEmpty()) return "请至少启用一个区域"
        if (active.any { r -> r.sourceRegionId != null && active.none { it.id == r.sourceRegionId } })
            return "已启用的依赖区域必须同时启用它的上游"
        return null
    }
}

data class AppConfig(
    val groups: List<RegionGroup> = listOf(RegionGroup(DEFAULT_GROUP_ID, "默认组")),
    val selectedGroupId: String = DEFAULT_GROUP_ID,
) {
    val selectedGroup get() = groups.first { it.id == selectedGroupId }
    val regions get() = selectedGroup.regions
    val screen get() = selectedGroup.screen

    fun updateSelected(transform: (RegionGroup) -> RegionGroup) = copy(
        groups = groups.map { if (it.id == selectedGroupId) transform(it) else it },
    )

    fun error(): String? {
        if (groups.isEmpty() || groups.none { it.id == selectedGroupId }) return "必须选定一个存在的组"
        if (groups.map { it.id }.distinct().size != groups.size) return "分组 ID 重复"
        if (groups.flatMap { it.regions }.map { it.id }.let { it.distinct().size != it.size }) return "区域 ID 重复"
        for (group in groups) {
            if (group.id.isBlank() || group.name.isBlank()) return "分组名称与 ID 不能为空"
            group.error()?.let { return "${group.name}：$it" }
        }
        return null
    }

    fun runError(space: ScreenSpace): String? = error() ?: selectedGroup.runError(space)
}
