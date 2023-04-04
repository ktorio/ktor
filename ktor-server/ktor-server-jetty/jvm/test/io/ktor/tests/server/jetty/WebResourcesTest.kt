/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.servlet.*
import io.ktor.server.testing.*
import org.eclipse.jetty.server.handler.*
import org.junit.*
import org.junit.rules.*
import java.io.*
import java.net.*
import kotlin.test.*
import kotlin.test.Test

private const val PlainTextContent = "plain text"
private const val HtmlContent = "<p>HTML</p>"

class WebResourcesTest {
    @get:Rule
    val testDir: TemporaryFolder = TemporaryFolder()

    lateinit var textFile: File
    lateinit var htmlFile: File

    @BeforeTest
    fun createFiles() {
        textFile = File(testDir.root, "1.txt").apply {
            writeText(PlainTextContent)
        }
        htmlFile = File(testDir.root, "2.html").apply {
            writeText(HtmlContent)
        }
    }

    @AfterTest
    fun cleanup() {
        testDir.root.deleteRecursively()
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

    private inner class TestContext : ContextHandler.StaticContext() {
        override fun getResource(path: String?): URL? {
            return when (path) {
                "/pages/index.txt" -> textFile
                "/pages/password.txt" -> textFile
                "/pages/index.html" -> htmlFile
                else -> null
            }?.toURI()?.toURL()
        }
    }
}
