/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlin.coroutines.*

@EngineAPI
public class DefaultApplicationEngineEnvironment(
    override val log: Logger,
    override val config: ApplicationConfig,
    override val connectors: List<EngineConnectorConfig>,
    private val modules: List<Application.() -> Unit>,
    override val parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    override val rootPath: String = "",
    override val developmentMode: Boolean = true
) : ApplicationEngineEnvironment {
    override val monitor: ApplicationEvents = ApplicationEvents()

    private var _application: Application? by atomic(null)

    override val application: Application get() = _application ?: error("Application was not started")

    override fun start() {
        val newInstance = Application(this)
        safeRiseEvent(ApplicationStarting, newInstance)
        try {
            modules.forEach { newInstance.it() }
        } catch (cause: Throwable) {
            log.error("Failed to start application.", cause)
            destroy(newInstance)
            throw cause
        }
        safeRiseEvent(ApplicationStarted, newInstance)
        _application = newInstance
    }

    override fun stop() {
        val currentApplication = _application ?: return
        _application = null
        destroy(currentApplication)
    }

    private fun destroy(application: Application) {
        safeRiseEvent(ApplicationStopping, application)
        try {
            application.dispose()
        } catch (e: Throwable) {
            log.error("Failed to destroy application instance.", e)
        }
        safeRiseEvent(ApplicationStopped, application)
    }

    private fun safeRiseEvent(event: EventDefinition<Application>, application: Application) {
        try {
            monitor.raise(event, application)
        } catch (cause: Throwable) {
            log.error("One or more of the handlers thrown an exception", cause)
        }
    }

}
