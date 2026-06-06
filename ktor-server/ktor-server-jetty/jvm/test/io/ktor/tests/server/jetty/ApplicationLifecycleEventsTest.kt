/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty

import io.ktor.server.servlet.KtorServletContextListener
import io.ktor.server.servlet.ServletApplicationEngine
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression test for KTOR-8524: when `ServletApplicationEngine` bootstraps itself from configuration
 * (the WAR deployment scenario), the application lifecycle is bound to the web application context via
 * [KtorServletContextListener] instead of the servlet's lazy `init()` / `destroy()` callbacks.
 *
 * As a result, regardless of `load-on-startup`:
 *  - `ApplicationStarted` is triggered at deployment time, before any request, and
 *  - `ApplicationStopped` is triggered on undeploy even if no request was ever served.
 *
 * The servlet is mounted like a real WAR — no engine attributes are injected and no `load-on-startup`
 * is configured. [KtorServletContextListener] is registered directly, mirroring what
 * [io.ktor.server.servlet.KtorServletContainerInitializer] does when a container auto-discovers it via
 * `META-INF/services` (validated separately below).
 */
class ApplicationLifecycleEventsTest {

    @BeforeTest
    fun resetProbe() {
        LifecycleProbe.reset()
    }

    @AfterTest
    fun clearProbe() {
        LifecycleProbe.reset()
    }

    @Test
    fun applicationStartedIsTriggeredAtDeploymentBeforeAnyRequest() {
        val server = startServer()
        try {
            assertTrue(
                "started" in LifecycleProbe.events,
                "ApplicationStarted must be triggered when the application is deployed, before any request is made"
            )

            // Managed mode still serves requests: the servlet reuses the listener-started server.
            httpGet(server.localPort())
        } finally {
            server.stop()
        }
    }

    @Test
    fun applicationStoppedIsTriggeredOnUndeployWithoutAnyRequest() {
        val server = startServer()
        server.stop()

        assertTrue(
            "stopped" in LifecycleProbe.events,
            "ApplicationStopped must be triggered on undeploy even if no request was ever served"
        )
    }

    @Test
    fun containerInitializerIsRegisteredViaServiceFile() {
        val resource = javaClass.classLoader
            .getResource("META-INF/services/javax.servlet.ServletContainerInitializer")
        assertNotNull(resource, "ServletContainerInitializer service file must be present for auto-registration")

        val registered = resource.readText().lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toList()
        assertTrue(
            "io.ktor.server.servlet.KtorServletContainerInitializer" in registered,
            "KtorServletContainerInitializer must be registered as a ServletContainerInitializer service"
        )
    }

    private fun startServer(): Server {
        val server = Server()
        server.addConnector(ServerConnector(server).apply { port = 0 })

        val context = ServletContextHandler().apply {
            classLoader = this@ApplicationLifecycleEventsTest::class.java.classLoader
            contextPath = "/"
            addServlet(
                ServletHolder("ktor-servlet", ServletApplicationEngine::class.java).apply {
                    isAsyncSupported = false
                    // Self-bootstrap mode: no ApplicationAttributeKey injection, configuration only.
                    setInitParameter("io.ktor.ktor.config", "application-lifecycle.conf")
                    // Intentionally NO load-on-startup configured — mirror a default WAR (lazy init).
                },
                "/*"
            )
            // Mirror what KtorServletContainerInitializer does when auto-discovered via META-INF/services.
            addEventListener(KtorServletContextListener())
        }
        server.handler = context

        server.start()
        return server
    }

    private fun Server.localPort(): Int = (connectors.first() as ServerConnector).localPort

    private fun httpGet(port: Int) {
        val connection = URI("http://localhost:$port/").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.responseCode // triggers the request
        } finally {
            connection.disconnect()
        }
    }
}
