/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.http2.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.servlet.jakarta.*
import jakarta.servlet.*
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.ee10.servlet.ServletMapping

// the factory and engine are only suitable for testing
// you shouldn't use it for production code

internal class Servlet(private val async: Boolean) :
    ApplicationEngineFactory<JettyServletApplicationEngine, JettyApplicationEngineBase.Configuration> {
    override fun configuration(
        configure: JettyApplicationEngineBase.Configuration.() -> Unit
    ): JettyApplicationEngineBase.Configuration {
        return JettyApplicationEngineBase.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: JettyApplicationEngineBase.Configuration,
        applicationProvider: () -> Application
    ): JettyServletApplicationEngine {
        return JettyServletApplicationEngine(
            environment,
            monitor,
            developmentMode,
            configuration,
            applicationProvider,
            async
        )
    }
}

internal class JettyServletApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configuration: Configuration,
    applicationProvider: () -> Application,
    async: Boolean
) : JettyApplicationEngineBase(environment, monitor, developmentMode, configuration, applicationProvider) {
    init {
        server.handler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            setAttribute(ServletApplicationEngine.EnvironmentAttributeKey, environment)
            setAttribute(ServletApplicationEngine.ApplicationAttributeKey, applicationProvider)
            setAttribute(ServletApplicationEngine.ApplicationEnginePipelineAttributeKey, pipeline)

            insertHandler(
                ServletHandler().apply {
                    val holder = ServletHolder(
                        "ktor-servlet",
                        ServletApplicationEngine::class.java
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
