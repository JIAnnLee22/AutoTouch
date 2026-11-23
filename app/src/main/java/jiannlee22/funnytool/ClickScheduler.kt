package jiannlee22.funnytool

import android.os.SystemClock
import java.util.PriorityQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Single actor: independent deadlines, but never concurrent dispatchGesture calls. */
class ClickScheduler(
    private val scope: CoroutineScope,
    private val tap: suspend (ClickRegion) -> Boolean,
    private val onCompleted: (ClickRegion) -> Unit,
    private val onError: (String) -> Unit,
) {
    private data class Event(val region: ClickRegion, val dueMs: Long, val sequence: Long)
    private var job: Job? = null

    fun stop() { job?.cancel(); job = null }

    fun start(config: AppConfig) {
        stop()
        job = scope.launch {
            try {
                val active = config.regions.filter { it.enabled }
                val queue = PriorityQueue(compareBy<Event> { it.dueMs }.thenBy { it.sequence })
                var sequence = 0L
                fun enqueue(region: ClickRegion, completedAtMs: Long) {
                    if (region.sourceRegionId != null) {
                        when (region.repeatPolicy) {
                            RepeatPolicy.IGNORE -> if (queue.any { it.region.id == region.id }) return
                            RepeatPolicy.RESTART -> queue.removeAll { it.region.id == region.id }
                            RepeatPolicy.QUEUE -> Unit
                        }
                    }
                    check(queue.size < 256) { "待执行事件超过 256 个，已安全停止；请降低频率" }
                    val delayMs = region.timing.sampleDelayMs()
                    check(delayMs <= Long.MAX_VALUE - completedAtMs) { "延迟超出单调时钟范围" }
                    queue.add(Event(region, completedAtMs + delayMs, sequence++))
                }
                val startedAtMs = SystemClock.elapsedRealtime()
                active.filter { it.sourceRegionId == null }.forEach { enqueue(it, startedAtMs) }
                while (queue.isNotEmpty()) {
                    coroutineContext.ensureActive()
                    // Re-evaluate periodically: delay's uptime clock excludes device sleep.
                    val next = queue.peek()!!
                    val waitMs = next.dueMs - SystemClock.elapsedRealtime()
                    if (waitMs > 0) { delay(minOf(waitMs, 1000)); continue }
                    queue.remove()
                    check(tap(next.region)) { "${next.region.name} 手势失败或落点被工具条遮挡，请移动工具条后重试" }
                    coroutineContext.ensureActive()
                    val completedAtMs = SystemClock.elapsedRealtime()
                    onCompleted(next.region)
                    if (next.region.sourceRegionId == null) enqueue(next.region, completedAtMs)
                    active.filter { it.sourceRegionId == next.region.id }.forEach { enqueue(it, completedAtMs) }
                    // Even zero-delay rules yield to stop/pause and system callbacks.
                    delay(1)
                }
            } catch (timeout: TimeoutCancellationException) {
                onError("手势回调超时，已停止；等待系统回调或重新连接服务后再开始")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "调度异常，已停止")
            }
        }
    }
}
