/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application

import io.ktor.util.logging.*
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
val Application.log: Logger get() = environment.log
