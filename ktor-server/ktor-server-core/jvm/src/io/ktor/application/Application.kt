/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application

import kotlinx.coroutines.*
import org.slf4j.*
import kotlin.coroutines.*

/**
 * Represents configured and running web application, capable of handling requests.
 * It is also the application coroutine scope that is cancelled immediately at application stop so useful
 * for launching background coroutines.
 *
 * @param environment Instance of [ApplicationEnvironment] describing environment this application runs in
 */
public class Application(
    override val environment: ApplicationEnvironment
) : ApplicationCallPipeline(environment.developmentMode, environment), CoroutineScope {

    private val applicationJob = SupervisorJob(environment.parentCoroutineContext[Job])

    override val coroutineContext: CoroutineContext = environment.parentCoroutineContext + applicationJob

    /**
     * Called by [ApplicationEngine] when [Application] is terminated
     */
    public fun dispose() {
        applicationJob.cancel()
        uninstallAllFeatures()
    }
}

/**
 * Convenience property to access log from application
 */
public val Application.log: Logger get() = environment.log
