/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import kotlinx.coroutines.*
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.registerIfAbsent

abstract class TestServerService : BuildService<TestServerService.Parameters>, AutoCloseable {

    private val logger = Logging.getLogger("TestServerService")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startServer()
    }

    private fun startServer() {
        logger.lifecycle("Starting test server...")
        try {
            startServer(scope, parameters.verbose.get())
        } catch (cause: Throwable) {
            scope.cancel()
            throw cause
        }
        logger.lifecycle("Test server started.")
    }

    override fun close() {
        logger.lifecycle("Stopping test server...")
        scope.cancel()
        runBlocking { scope.coroutineContext.job.join() }
        logger.lifecycle("Test server stopped.")
    }

    interface Parameters : BuildServiceParameters {
        val verbose: Property<Boolean>
    }

    companion object {
        fun registerIfAbsent(project: Project): Provider<TestServerService> {
            val verbose = project.providers.gradleProperty("ktorbuild.testServer.verbose")
                .map { it.toBoolean() }
                .orElse(false)
            return project.gradle.sharedServices.registerIfAbsent("testServer", TestServerService::class) {
                parameters.verbose.set(verbose)
            }
        }
    }
}
