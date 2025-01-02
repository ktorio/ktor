/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.servlet.jakarta.*
import io.ktor.server.testing.*
import org.eclipse.jetty.server.handler.ContextHandler
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.*

private const val PlainTextContent = "plain text"
private const val HtmlContent = "<p>HTML</p>"

class WebResourcesTest {
    @TempDir
    lateinit var testDir: Path

    lateinit var textFile: Path
    lateinit var htmlFile: Path

    @BeforeTest
    fun createFiles() {
        textFile = testDir.resolve("1.txt").apply {
            writeText(PlainTextContent)
        }
        htmlFile = testDir.resolve("2.html").apply {
            writeText(HtmlContent)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun cleanup() {
        testDir.deleteRecursively()
    }

    @Test
    fun testServeWebResources() = testApplication {
        application {
            attributes.put(ServletContextAttribute, TestContext())
            routing {
                route("webapp") {
                    webResources("pages") {
                        include { it.endsWith(".txt") }
                        include { it.endsWith(".html") }
                        exclude { it.endsWith("password.txt") }
                    }
                }
            }
        }

        val client = createClient { expectSuccess = false }
        client.get("/webapp/index.txt").bodyAsText().let {
            assertEquals(PlainTextContent, it)
        }
        client.get("/webapp/index.html").bodyAsText().let {
            assertEquals(HtmlContent, it)
        }
        client.get("/webapp/password.txt").let {
            assertFalse(it.status.isSuccess())
        }
    }

    /*
    FIXME what is the replacement for ContextHandler.StaticContext?
    Maybe ResourceHandler should be used, but there is no getResource() method
     */
    private inner class TestContext : ContextHandler.StaticContext() {
        override fun getResource(path: String?): URL? {
            return when (path) {
                "/pages/index.txt" -> textFile
                "/pages/password.txt" -> textFile
                "/pages/index.html" -> htmlFile
                else -> null
            }?.toUri()?.toURL()
        }
    }
}
