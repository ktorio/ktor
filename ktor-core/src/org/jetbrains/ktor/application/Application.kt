package org.jetbrains.ktor.application

import java.util.concurrent.*

/**
 * Represents configured and running web application, capable of handling requests
 */
open class Application(val environment: ApplicationEnvironment, forMigrationPurposeOnly: Unit) : ApplicationCallPipeline() {
    @Deprecated("Don't inherit from Application class, inherit ApplicationModule instead and override `install`")
    constructor(environment: ApplicationEnvironment) : this(environment, Unit) // TODO: drop primary constructor unit parameter when remove this

    /**
     * Called by host when [Application] is terminated
     */
    open fun dispose() {
        uninstallAllFeatures()
    }
}

/**
 * Convenience property to access log from application
 */
val Application.log get() = environment.log
val Application.executor get() = environment.executor
