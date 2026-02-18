/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty

import io.ktor.client.statement.*
import io.ktor.server.jetty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.servlet.*
import io.ktor.server.testing.suites.*
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.DefaultHandler
import org.eclipse.jetty.util.component.LifeCycle
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class JettyCompressionTest :
    CompressionTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyContentTest : ContentTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyHttpServerCommonTest :
    HttpServerCommonTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    override fun testFlushingHeaders() {
        // no op
    }
}

class JettyHttpServerJvmTest : HttpServerJvmTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    override fun configure(configuration: JettyApplicationEngineBase.Configuration) {
        super.configure(configuration)
        configuration.configureServer = {
            addAttributesHandler()
        }
    }

    @Test
    fun testServletAttributes() = runTest {
        createAndStartServer {
            get("/tomcat/attributes") {
                call.respondText(
                    call.request.servletRequestAttributes["ktor.test.attribute"]?.toString() ?: "Not found"
                )
            }
        }

        withUrl("/tomcat/attributes", {}) {
            assertEquals("135", call.response.bodyAsText())
        }
    }

    private fun Server.addAttributesHandler() {
        addLifeCycleListener(
            object : LifeCycle.Listener {
                override fun lifeCycleStarting(event: LifeCycle?) {
                    super.lifeCycleStarting(event)
                    val delegate = handler
                    handler = object : DefaultHandler() {
                        override fun handle(
                            target: String?,
                            baseRequest: Request?,
                            request: HttpServletRequest?,
                            response: HttpServletResponse?
                        ) {
                            request?.setAttribute("ktor.test.attribute", "135")
                            delegate?.handle(target, baseRequest, request, response)
                        }
                    }
                }
            }
        )
    }

    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class JettySustainabilityTest :
    SustainabilityTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    @Ignore
    override fun validateCallCoroutineContext() {}
}

class JettyConfigTest : ConfigTestSuite(Jetty)

class JettyConnectionTest : ConnectionTestSuite(Jetty)

class JettyServerPluginsTest : ServerPluginsTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
