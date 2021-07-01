/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.json

import io.ktor.http.*
import kotlin.test.*

class JsonPluginTest {
    @Test
    fun testDefaultContentTypes() {
        val config = JsonPlugin.Config()

        assertEquals(1, config.acceptContentTypes.size)
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Json) }

        val plugin = JsonPlugin(config)
        assertTrue { plugin.canHandle(ContentType.parse("application/json")) }
        assertTrue { plugin.canHandle(ContentType.parse("application/vnd.foo+json")) }
        assertFalse { plugin.canHandle(ContentType.parse("text/json")) }
    }

    @Test
    fun testAcceptCall() {
        val config = JsonPlugin.Config()
        config.accept(ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Json) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Xml) }
    }

    @Test
    fun testContentTypesListAssignment() {
        val config = JsonPlugin.Config()
        config.acceptContentTypes = listOf(ContentType.Application.Pdf, ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertFalse { config.acceptContentTypes.contains(ContentType.Application.Json) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Xml) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Pdf) }
    }

    @Test
    fun testContentTypesFilter() {
        val config = JsonPlugin.Config().apply {
            receive(
                object : ContentTypeMatcher {
                    override fun contains(contentType: ContentType): Boolean {
                        return contentType.toString() == "text/json"
                    }
                }
            )
        }

        val plugin = JsonPlugin(config)
        assertTrue { plugin.canHandle(ContentType.parse("text/json")) }
    }
}
