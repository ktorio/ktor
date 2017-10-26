package io.ktor.application

/**
 * Represents configured and running web application, capable of handling requests
 *
 * @param environment Instance of [ApplicationEnvironment] describing environment this application runs in
 */
class Application(val environment: ApplicationEnvironment) : ApplicationCallPipeline() {
    /**
     * Called by [ApplicationEngine] when [Application] is terminated
     */
    fun dispose() {
        uninstallAllFeatures()
    }
}

/**
 * Convenience property to access log from application
 */
val Application.log get() = environment.log
