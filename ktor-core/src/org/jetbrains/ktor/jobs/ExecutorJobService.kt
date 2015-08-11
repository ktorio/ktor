package org.jetbrains.ktor.jobs

import org.jetbrains.ktor.application.*
import java.util.*
import java.util.concurrent.*

open public class ExecutorJobService(val log: ApplicationLog) : JobService {
    val executor: ScheduledExecutorService by lazy { Executors.newScheduledThreadPool(4) }

    public override fun async(name: String, body: () -> Unit) {
        executor.submit {
            try {
                body.invoke()
                log.trace("Task [$name] finished.")
            } catch (e: Exception) {
                log.trace("Task [$name] failed.")
                log.error(e)
            }
        }
        log.trace("Scheduled task [$name].")
    }

    public override fun schedule(name: String, seconds: Long, body: () -> Unit) {
        val shiftTime = Math.abs(Random().nextLong() % seconds)
        executor.scheduleWithFixedDelay({
                                            try {
                                                log.trace("Job [$name] triggered.")
                                                body.invoke()
                                                log.trace("Job [$name] done.")
                                            } catch (e: Exception) {
                                                log.trace("Job [$name] failed.")
                                                log.error(e)
                                            }
                                        }, shiftTime, seconds, TimeUnit.SECONDS)
        log.info("Scheduled periodic job [$name] in $shiftTime secs with period $seconds secs.")
    }
}