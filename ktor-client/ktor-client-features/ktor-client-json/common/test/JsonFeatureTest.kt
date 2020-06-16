/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import io.ktor.http.*
import kotlin.test.*

class JsonFeatureTest {
    @Test
    fun testDefaultContentTypes() {
        val config = JsonFeature.Config()

        assertEquals(1, config.acceptContentTypes.size)
        assertTrue { config.matchesContentType(ContentType.Application.Json) }

        assertTrue { config.matchesContentType(ContentType.parse("application/json")) }
        assertTrue { config.matchesContentType(ContentType.parse("application/vnd.foo+json")) }
        assertFalse { config.matchesContentType(ContentType.parse("text/json")) }
    }

    @Test
    fun testAcceptCall() {
        val config = JsonFeature.Config()
        config.accept(ContentType.Application.Xml)
        config.accept(ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertTrue { config.matchesContentType(ContentType.Application.Json) }
        assertTrue { config.matchesContentType(ContentType.Application.Xml) }
    }

    @Test
    fun testContentTypesListAssignment() {
        val config = JsonFeature.Config()
        config.acceptContentTypes = listOf(ContentType.Application.Pdf, ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertFalse { config.matchesContentType(ContentType.Application.Json) }
        assertTrue { config.matchesContentType(ContentType.Application.Xml) }
        assertTrue { config.matchesContentType(ContentType.Application.Pdf) }
    }

    @Test
    fun testContentTypesFilter() {
        val config = JsonFeature.Config().apply {
            acceptContentTypes = listOf(object : ContentTypeMatcher {
                override fun match(contentType: ContentType): Boolean =
                    contentType.match("text/json")
            })
        }

        assertFalse { config.matchesContentType(ContentType.parse("application/json")) }
        assertFalse { config.matchesContentType(ContentType.parse("application/vnd.foo+json")) }
        assertTrue { config.matchesContentType(ContentType.parse("text/json")) }
    }
}
