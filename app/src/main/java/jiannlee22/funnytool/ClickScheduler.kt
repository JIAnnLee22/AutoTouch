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

/** Each group trigger is one non-overlapping pass of its enabled dependency forest. */
class ClickScheduler(
    private val scope: CoroutineScope,
    private val tap: suspend (ClickRegion) -> Boolean,
    private val progress: () -> RuntimeState,
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
                while (progress().completed < progress().targetTriggerCount) {
                    coroutineContext.ensureActive()
                    val triggerIndex = progress().completed
                    val queue = PriorityQueue(compareBy<Event> { it.dueMs }.thenBy { it.sequence })
                    var sequence = 0L
                    fun enqueue(region: ClickRegion, completedAtMs: Long) {
                        val clicked = progress().completedClicksByRegion[region.id] ?: 0L
                        if (region.id in progress().completedRegionIds || clicked >= region.clicksPerTrigger ||
                            queue.any { it.region.id == region.id }) return
                        check(queue.size < 256) { "待执行事件超过 256 个，已安全停止" }
                        // Only the first click pays the trigger delay. Later clicks (including
                        // resumed partial batches) use their independent random interval alone.
                        val delayMs = if (clicked == 0L) region.timing.sampleDelayMs() else region.sampleClickIntervalMs()
                        check(delayMs <= Long.MAX_VALUE - completedAtMs) { "延迟超出单调时钟范围" }
                        queue.add(Event(region, completedAtMs + delayMs, sequence++))
                    }
                    val startedAtMs = SystemClock.elapsedRealtime()
                    // Resume partial batches without replaying successful clicks or the first
                    // delay; finished upstream batches unlock children with a fresh first delay.
                    active.filter { (progress().completedClicksByRegion[it.id] ?: 0L) > 0 ||
                        it.sourceRegionId == null || it.sourceRegionId in progress().completedRegionIds }
                        .forEach { enqueue(it, startedAtMs) }
                    while (queue.isNotEmpty() && progress().completed == triggerIndex) {
                        coroutineContext.ensureActive()
                        val next = queue.peek()!!
                        val waitMs = next.dueMs - SystemClock.elapsedRealtime()
                        if (waitMs > 0) { delay(minOf(waitMs, 1000)); continue }
                        queue.remove()
                        check(tap(next.region)) { "${next.region.name} 手势未完成，请检查解锁状态、屏幕边界与工具条遮挡" }
                        coroutineContext.ensureActive()
                        if (progress().completed != triggerIndex) break
                        val completedAtMs = SystemClock.elapsedRealtime()
                        if (next.region.id in progress().completedRegionIds) {
                            active.filter { it.sourceRegionId == next.region.id }.forEach { enqueue(it, completedAtMs) }
                        } else enqueue(next.region, completedAtMs)
                        delay(1)
                    }
                    check(progress().completed > triggerIndex) { "当前组没有可执行任务，请检查依赖配置" }
                    delay(1)
                }
                // The service owns completion in the successful gesture callback, including
                // when the final in-flight gesture finishes while this actor is paused.
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
