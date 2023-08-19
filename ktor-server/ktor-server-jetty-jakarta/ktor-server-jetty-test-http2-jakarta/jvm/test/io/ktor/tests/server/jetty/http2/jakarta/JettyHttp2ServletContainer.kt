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
import org.eclipse.jetty.servlet.*

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
            async
        )
    }
}

internal class JettyServletApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configuration: Configuration,
    async: Boolean
) : JettyApplicationEngineBase(environment, monitor, developmentMode, configuration) {
    init {
        server.handler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            val embeddedServer = EmbeddedServer(applicationProperties(environment), EmptyEngineFactory)
            setAttribute(ServletApplicationEngine.EmbeddedServerAttributeKey, embeddedServer)
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

private object EmptyEngineFactory : ApplicationEngineFactory<ApplicationEngine, ApplicationEngine.Configuration> {
    override fun configuration(
        configure: ApplicationEngine.Configuration.() -> Unit
    ): ApplicationEngine.Configuration {
        return ApplicationEngine.Configuration()
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: ApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): ApplicationEngine {
        return object : ApplicationEngine {
            override val environment: ApplicationEnvironment = environment
            override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
            override fun start(wait: Boolean): ApplicationEngine = this
            override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) = Unit
        }
    }
}
