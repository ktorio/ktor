/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.servlet.jakarta.*
import jakarta.servlet.MultipartConfigElement
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.ee10.servlet.ServletMapping


/**
 * The factory and engine are only suitable for testing. You shouldn't use it for production code.
 */
internal class Servlet(
    private val async: Boolean
) : ApplicationEngineFactory<JettyServletApplicationEngine, JettyApplicationEngineBase.Configuration> {
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
        server.handler = ServletContextHandler().apply<ServletContextHandler> {
            this.classLoader = environment.classLoader
            setAttribute(ServletApplicationEngine.EnvironmentAttributeKey, environment)
            setAttribute(ServletApplicationEngine.ApplicationAttributeKey, applicationProvider)
            setAttribute(ServletApplicationEngine.ApplicationEnginePipelineAttributeKey, pipeline)

            insertHandler(
                ServletHandler().apply<ServletHandler> {
                    addServlet(
                        ServletHolder("ktor-servlet", ServletApplicationEngine::class.java).apply<ServletHolder> {
                            this.isAsyncSupported = async
                            this.registration.setLoadOnStartup(1)
                            this.registration.setMultipartConfig(
                                MultipartConfigElement(System.getProperty("java.io.tmpdir"))
                            )
                            this.registration.setAsyncSupported(async)
                        }
                    )
                    addServletMapping(
                        ServletMapping().apply<ServletMapping> {
                            this.pathSpecs = arrayOf<String>("*.", "/*")
                            this.servletName = "ktor-servlet"
                        }
                    )
                }
            )
        }
    }
}
