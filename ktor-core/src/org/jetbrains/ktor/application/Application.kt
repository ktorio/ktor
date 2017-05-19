package org.jetbrains.ktor.application

/**
 * Represents configured and running web application, capable of handling requests
 */
class Application(val environment: ApplicationEnvironment) : ApplicationCallPipeline() {
    /**
     * Called by host when [Application] is terminated
     */
    fun dispose() {
        uninstallAllFeatures()
    }
}

/**
 * Convenience property to access log from application
 */
val Application.log get() = environment.log
