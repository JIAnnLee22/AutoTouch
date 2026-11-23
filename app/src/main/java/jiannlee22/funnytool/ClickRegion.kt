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
        // Unsigned sampling avoids max + 1 overflow even for 0..Long.MAX_VALUE.
        val width = randomDelayMaxMs.toULong() - randomDelayMinMs.toULong() + 1uL
        val offset = if (width == 1uL) 0uL else random.nextULongBelow(width)
        return baseDelayMs + randomDelayMinMs + offset.toLong()
    }
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
    // null means independent; a dependency is one tap per successful upstream completion.
    val sourceRegionId: String? = null,
    val repeatPolicy: RepeatPolicy = RepeatPolicy.IGNORE,
    val pressDurationMs: Long = 50,
) {
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

data class AppConfig(val regions: List<ClickRegion> = emptyList(), val screen: ScreenSpace? = null) {
    fun error(): String? {
        if (regions.isEmpty()) return null
        val space = screen ?: return "请先编辑区域，确认屏幕坐标"
        if (!space.valid()) return "屏幕安全边界无效"
        if (regions.map { it.id }.distinct().size != regions.size) return "区域 ID 重复"
        val byId = regions.associateBy { it.id }
        for (region in regions) {
            if (region.id.isBlank() || region.name.isBlank()) return "区域名称与 ID 不能为空"
            region.timing.error()?.let { return "${region.name}：$it" }
            if (region.pressDurationMs !in 1..200) return "${region.name}：首版短按需为 1~200 ms"
            if (!region.fits(space)) return "${region.name}：圆形必须完整处于安全屏幕内"
            if (region.sourceRegionId != null && region.sourceRegionId !in byId) return "${region.name}：上游区域不存在"
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
        if (screen != space) return "屏幕尺寸或安全边界已变化，请重新编辑区域"
        val active = regions.filter { it.enabled }
        if (active.isEmpty()) return "请至少启用一个区域"
        if (active.any { r -> r.sourceRegionId != null && active.none { it.id == r.sourceRegionId } })
            return "已启用的依赖区域必须同时启用它的上游"
        return null
    }
}
