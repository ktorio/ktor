/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.tomcat

import io.ktor.application.*
import io.ktor.client.statement.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.servlet.*
import io.ktor.server.testing.suites.*
import io.ktor.server.tomcat.*
import org.apache.catalina.core.*
import org.apache.tomcat.util.descriptor.web.*
import java.util.logging.*
import javax.servlet.*
import javax.servlet.Filter
import kotlin.test.*

class TomcatCompressionTest :
    CompressionTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }
}

class TomcatContentTest :
    ContentTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }
}

class TomcatHttpServerTest :
    HttpServerTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }

    @Ignore
    @Test
    override fun testUpgrade() {
        super.testUpgrade()
    }

    override fun configure(configuration: TomcatApplicationEngine.Configuration) {
        super.configure(configuration)
        configuration.configureTomcat = {
            addAttributesFilter()
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

        withUrl("/tomcat/attributes") {
            assertEquals("135", call.response.readText())
        }
    }

    private fun org.apache.catalina.startup.Tomcat.addAttributesFilter() {
        server.addLifecycleListener {
            host.findChildren().forEach {
                if (it is StandardContext) {
                    if (it.findFilterConfig("AttributeFilter") == null) {
                        it.addFilterDef(
                            FilterDef().apply {
                                filterName = "AttributeFilter"
                                filterClass = AttributeFilter::class.java.name
                                filter = AttributeFilter()
                            }
                        )
                        it.addFilterMap(
                            FilterMap().apply {
                                addURLPattern("/*")
                                filterName = "AttributeFilter"
                            }
                        )
                    }
                }
            }
        }
    }

    internal class AttributeFilter : Filter {
        override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
            request.setAttribute("ktor.test.attribute", "135")
            chain.doFilter(request, response)
        }
    }
}

class TomcatSustainabilityTestSuite :
    SustainabilityTestSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat) {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
        enableHttp2 = false
    }

    /**
     * Tomcat trim `vspace` symbol and drop content-length. The request is treated as chunked.
     */
    @Ignore
    @Test
    override fun testChunkedWithVSpace() {
        super.testChunkedWithVSpace()
    }

    @Ignore
    @Test
    override fun testBlockingConcurrency() {
        super.testBlockingConcurrency()
    }
}

class TomcatConfigTest : ConfigTestSuite(Tomcat)
