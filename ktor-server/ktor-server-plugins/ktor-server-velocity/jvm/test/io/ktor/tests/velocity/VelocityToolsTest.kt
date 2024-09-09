/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused", "UNUSED_PARAMETER")

package io.ktor.tests.velocity

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.velocity.*
import org.apache.velocity.runtime.resource.loader.*
import org.apache.velocity.runtime.resource.util.*
import org.apache.velocity.tools.config.*
import java.util.*
import kotlin.test.*

class VelocityToolsTest {

    @Test
    fun testBareVelocity() = testApplication {
        setUpTestTemplates()
        install(ConditionalHeaders)

        routing {
            val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

            get("/") {
                call.respondTemplate("test.vl", model)
            }
        }

        val response = client.get("/")

        with(response) {
            val lines = bodyAsText().lines()

            assertEquals("<p>Hello, 1</p>", lines[0])
            assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
            assertEquals("<i></i>", lines[2])
        }
    }

    @Test
    fun testStandardTools() = testApplication {
        setUpTestTemplates {
            addDefaultTools()
            setProperty("locale", "en_US")
        }
        install(ConditionalHeaders)

        routing {
            val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

            get("/") {
                call.respondTemplate("test.vl", model)
            }
        }

        val response = client.get("/")

        with(response) {
            val lines = bodyAsText().lines()

            assertEquals("<p>Hello, 1</p>", lines[0])
            assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
            assertEquals("<i>October</i>", lines[2])
        }
    }

    class DateTool {
        fun toDate(vararg any: Any): Date? = null
        fun format(vararg any: Any) = "today"
    }

    @Test
    fun testCustomTool() = testApplication {
        setUpTestTemplates {
            tool(DateTool::class.java)
        }
        install(ConditionalHeaders)

        routing {
            val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

            get("/") {
                call.respondTemplate("test.vl", model)
            }
        }

        val response = client.get("/")

        with(response) {
            val lines = bodyAsText().lines()

            assertEquals("<p>Hello, 1</p>", lines[0])
            assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
            assertEquals("<i>today</i>", lines[2])
        }
    }

    @Test
    fun testConfigureLocale() = testApplication {
        setUpTestTemplates {
            addDefaultTools()
            setProperty("locale", "fr_FR")
        }
        install(ConditionalHeaders)

        routing {
            val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

            get("/") {
                call.respondTemplate("test.vl", model)
            }
        }

        val response = client.get("/")

        with(response) {
            val lines = bodyAsText().lines()

            assertEquals("<p>Hello, 1</p>", lines[0])
            assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
            assertEquals("<i>octobre</i>", lines[2])
        }
    }

    @Test
    fun testNoVelocityConfig() = testApplication {
        install(VelocityTools)

        routing {
            val model = mapOf("id" to 1, "title" to "Bonjour le monde!")

            get("/") {
                // default engine resource loader is 'file',
                // default test working directory is ktor-velocity project root
                call.respondTemplate("jvm/test/resources/test-template.vhtml", model)
            }
        }

        val response = client.get("/")

        with(response) {
            val lines = bodyAsText().lines()

            assertEquals("<p>Hello, 1</p>", lines[0])
            assertEquals("<h1>Bonjour le monde!</h1>", lines[1])
            assertEquals("<i></i>", lines[2])
        }
    }

    private fun ApplicationTestBuilder.setUpTestTemplates(config: EasyFactoryConfiguration.() -> Unit = {}) {
        val bax = "$"

        install(VelocityTools) {
            engine {
                setProperty("resource.loader", "string")
                addProperty("resource.loader.string.name", "myRepo")
                addProperty("resource.loader.string.class", StringResourceLoader::class.java.name)
                addProperty("resource.loader.string.repository.name", "myRepo")
            }
            apply(config)

            StringResourceRepositoryImpl().apply {
                putStringResource(
                    "test.vl",
                    """
                    <p>Hello, ${bax}id</p>
                    <h1>${bax}title</h1>
                    <i>$bax!{date.format('MMMM', ${bax}date.toDate('intl', '2003-10-07 03:14:50'))}</i>
                    """.trimIndent()
                )
                StringResourceLoader.setRepository("myRepo", this)
            }
        }
    }
}
