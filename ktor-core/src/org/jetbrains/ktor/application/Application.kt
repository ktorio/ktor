package org.jetbrains.ktor.application

import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * Represents configured and running web application, capable of handling requests
 */
open class Application(val environment: ApplicationEnvironment) : ApplicationCallPipeline() {
    private val threadCounter = AtomicInteger()

    val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), { r ->
        Thread(r, "ktor-pool-thread-${threadCounter.incrementAndGet()}")
    })

    /**
     * Called by host when [Application] is terminated
     */
    open fun dispose() {
        executor.shutdown()
        if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
            environment.log.warning("Failed to stop application executor service")
            executor.shutdownNow()
        }
    }
}
