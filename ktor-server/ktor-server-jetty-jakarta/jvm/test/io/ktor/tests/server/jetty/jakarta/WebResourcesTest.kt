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
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.junit.jupiter.api.io.*
import java.net.*
import java.nio.file.*
import kotlin.io.path.*
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
            attributes.put(ServletContextAttribute, testContext())
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

    private inner class TestContextHandler : ServletContextHandler() {
        inner class TestContext : ServletContextApi() {
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

    fun testContext(): ServletContextHandler.ServletContextApi =
        TestContextHandler().TestContext()
}
