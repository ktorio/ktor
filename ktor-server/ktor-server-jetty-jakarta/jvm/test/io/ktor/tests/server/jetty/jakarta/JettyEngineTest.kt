/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty.jakarta

import io.ktor.client.statement.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.servlet.jakarta.*
import io.ktor.server.testing.suites.*
import jakarta.servlet.http.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.util.component.*
import kotlin.test.*

class JettyCompressionTest :
    CompressionTestSuite<JettyServerEngine, JettyServerEngineBase.Configuration>(Jetty)

class JettyContentTest : ContentTestSuite<JettyServerEngine, JettyServerEngineBase.Configuration>(Jetty)

class JettyHttpServerCommonTest :
    HttpServerCommonTestSuite<JettyServerEngine, JettyServerEngineBase.Configuration>(Jetty) {
    override fun testFlushingHeaders() {
        // no op
    }
}

class JettyHttpServerJvmTest : HttpServerJvmTestSuite<JettyServerEngine, JettyServerEngineBase.Configuration>(
    Jetty
) {
    override fun configure(configuration: JettyServerEngineBase.Configuration) {
        super.configure(configuration)
        configuration.configureServer = {
            addAttributesHandler()
        }
    }

    @Test
    fun testServletAttributes() {
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
        addEventListener(
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
    SustainabilityTestSuite<JettyServerEngine, JettyServerEngineBase.Configuration>(Jetty)

class JettyConfigTest : ConfigTestSuite(Jetty)

class JettyConnectionTest : ConnectionTestSuite(Jetty)

class JettyServerPluginsTest : ServerPluginsTestSuite<JettyServerEngine, JettyServerEngineBase.Configuration>(
    Jetty
) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
