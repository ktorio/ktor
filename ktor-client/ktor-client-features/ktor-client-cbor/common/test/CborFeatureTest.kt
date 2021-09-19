/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.cbor

import io.ktor.http.*
import kotlin.test.*

class CborFeatureTest {
    @Test
    fun testDefaultContentTypes() {
        val config = CborFeature.Config()

        assertEquals(1, config.acceptContentTypes.size)
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Cbor) }

        val feature = CborFeature(config)
        assertTrue { feature.canHandle(ContentType.parse("application/cbor")) }
        assertTrue { feature.canHandle(ContentType.parse("application/vnd.foo+cbor")) }
        assertFalse { feature.canHandle(ContentType.parse("text/cbor")) }
    }

    @Test
    fun testAcceptCall() {
        val config = CborFeature.Config()
        config.accept(ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Cbor) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Xml) }
    }

    @Test
    fun testContentTypesListAssignment() {
        val config = CborFeature.Config()
        config.acceptContentTypes = listOf(ContentType.Application.Pdf, ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertFalse { config.acceptContentTypes.contains(ContentType.Application.Cbor) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Xml) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Pdf) }
    }

    @Test
    fun testContentTypesFilter() {
        val config = CborFeature.Config().apply {
            receive(
                object : ContentTypeMatcher {
                    override fun contains(contentType: ContentType): Boolean {
                        return contentType.toString() == "text/cbor"
                    }
                }
            )
        }

        val feature = CborFeature(config)
        assertTrue { feature.canHandle(ContentType.parse("text/cbor")) }
    }
}
