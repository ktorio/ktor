/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.servlet.jakarta.*
import jakarta.servlet.*
import org.eclipse.jetty.servlet.*

/**
 * The factory and engine are only suitable for testing. You shouldn't use it for production code.
 */
internal class Servlet(
    private val async: Boolean
) : ServerEngineFactory<JettyServletServerEngine, JettyServerEngineBase.Configuration> {
    override fun configuration(
        configure: JettyServerEngineBase.Configuration.() -> Unit
    ): JettyServerEngineBase.Configuration {
        return JettyServerEngineBase.Configuration().apply(configure)
    }

    override fun create(
        environment: ServerEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: JettyServerEngineBase.Configuration,
        serverProvider: () -> Server
    ): JettyServletServerEngine {
        return JettyServletServerEngine(
            environment,
            monitor,
            developmentMode,
            configuration,
            serverProvider,
            async
        )
    }
}

internal class JettyServletServerEngine(
    environment: ServerEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configuration: Configuration,
    serverProvider: () -> Server,
    async: Boolean
) : JettyServerEngineBase(environment, monitor, developmentMode, configuration, serverProvider) {
    init {
        server.handler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            setAttribute(ServletServerEngine.EnvironmentAttributeKey, environment)
            setAttribute(ServletServerEngine.ApplicationAttributeKey, serverProvider)
            setAttribute(ServletServerEngine.ApplicationEnginePipelineAttributeKey, pipeline)

            insertHandler(
                ServletHandler().apply {
                    val holder = ServletHolder(
                        "ktor-servlet",
                        ServletServerEngine::class.java
                    ).apply {
                        isAsyncSupported = async
                        registration.setLoadOnStartup(1)
                        registration.setMultipartConfig(MultipartConfigElement(System.getProperty("java.io.tmpdir")))
                        registration.setAsyncSupported(async)
                    }

                    addServlet(holder)
                    addServletMapping(
                        ServletMapping().apply {
                            pathSpecs = arrayOf("*.", "/*")
                            servletName = "ktor-servlet"
                        }
                    )
                }
            )
        }
    }
}
