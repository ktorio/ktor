/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import org.gradle.api.*
import org.gradle.api.logging.*
import org.gradle.api.provider.Provider
import org.gradle.api.services.*
import org.gradle.api.tasks.testing.*
import org.gradle.kotlin.dsl.*
import java.io.*

class TestServerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val testServerService = TestServerService.registerIfAbsent(target)

        target.tasks.withType<AbstractTestTask>().configureEach {
            usesService(testServerService)
            // Trigger server start if it is not started yet
            doFirst("start test server") { testServerService.get() }
        }
    }
}

private abstract class TestServerService : BuildService<BuildServiceParameters.None>, AutoCloseable {

    private val logger = Logging.getLogger("TestServerService")
    private val server: Closeable

    init {
        logger.lifecycle("Starting test server...")
        server = startServer()
        logger.lifecycle("Test server started.")
    }

    override fun close() {
        logger.lifecycle("Stopping test server...")
        server.close()
        logger.lifecycle("Test server stopped.")
    }

    companion object {
        fun registerIfAbsent(project: Project): Provider<TestServerService> {
            return project.gradle.sharedServices.registerIfAbsent("testServer", TestServerService::class)
        }
    }
}
