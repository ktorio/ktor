/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.registerIfAbsent
import java.io.Closeable

abstract class TestServerService : BuildService<BuildServiceParameters.None>, AutoCloseable {

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
