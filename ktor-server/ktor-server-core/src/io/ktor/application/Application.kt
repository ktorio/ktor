package io.ktor.application

import kotlinx.coroutines.*

/**
 * Represents configured and running web application, capable of handling requests.
 * It is also the application coroutine scope that is cancelled immediately at application stop so useful
 * for launching background coroutines.
 *
 * @param environment Instance of [ApplicationEnvironment] describing environment this application runs in
 */
class Application(val environment: ApplicationEnvironment) : ApplicationCallPipeline(), CoroutineScope {
    private val applicationJob = SupervisorJob(environment.parentCoroutineContext[Job])

    override val coroutineContext = environment.parentCoroutineContext + applicationJob

    /**
     * Called by [ApplicationEngine] when [Application] is terminated
     */
    fun dispose() {
        applicationJob.cancel()
        uninstallAllFeatures()
    }
}

/**
 * Convenience property to access log from application
 */
val Application.log get() = environment.log
