package org.jetbrains.ktor.application

import java.util.concurrent.*

/**
 * Represents configured and running web application, capable of handling requests
 */
open class Application(val environment: ApplicationEnvironment) : ApplicationCallPipeline() {
    val executor: ScheduledExecutorService = environment.executorServiceBuilder()

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
