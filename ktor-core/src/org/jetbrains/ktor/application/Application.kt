package org.jetbrains.ktor.application

import org.jetbrains.ktor.features.*
import java.util.concurrent.*

/**
 * Represents configured and running web application, capable of handling requests
 */
open class Application(val environment: ApplicationEnvironment, forMigrationPurposeOnly: Unit) : ApplicationCallPipeline() {
    @Deprecated("Don't inherit from Application class but do from ApplicationFeature")
    constructor(environment: ApplicationEnvironment) : this(environment, Unit) // TODO: drop primary constructor unit parameter when remove this

    val executor: ScheduledExecutorService = environment.executorServiceBuilder()

    /**
     * Called by host when [Application] is terminated
     */
    open fun dispose() {
        uninstallAllFeatures()
        executor.shutdown()
        if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
            environment.log.warning("Failed to stop application executor service")
            executor.shutdownNow()
        }
    }
}
