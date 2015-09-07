package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.junit.*
import kotlin.test.*

public class ContentTypeMatchTest {
    @Test
    fun testTypeAndSubtype() {
        assertTrue { ContentType.parse("text/plain").match("*/*") }
        assertTrue { ContentType.parse("text/plain").match("*/plain") }
        assertTrue { ContentType.parse("text/plain").match("text/*") }
        assertTrue { ContentType.parse("text/plain").match("text/plain") }

        assertFalse(ContentType.parse("text/plain").match("image/plain"))
        assertFalse(ContentType.parse("text/plain").match("text/xml"))
    }

    @Test
    fun testParametersConstants() {
        assertTrue { ContentType.parse("a/b; a=1").match("*/*; a=1") }
        assertFalse(ContentType.parse("a/b").match("*/*; a=2"))
        assertFalse(ContentType.parse("a/b; a=1").match("*/*; a=2"))
    }

    @Test
    fun testParametersWithSubtype() {
        assertTrue { ContentType.parse("a/b; a=1").match("a/b") }
    }

    @Test
    fun testParametersValueWildcard() {
        assertTrue(ContentType.parse("a/b; a=1").match("*/*; a=*"))
        assertFalse(ContentType.parse("a/b; b=1").match("*/*; a=*"))
    }

    @Test
    fun testParametersNameWildcard() {
        assertTrue(ContentType.parse("a/b; a=1").match("*/*; *=1"))
        assertFalse(ContentType.parse("a/b; a=2").match("*/*; *=1"))
    }

    @Test
    fun testParametersAllWildcard() {
        assertTrue(ContentType.parse("a/b; a=2").match("*/*; *=*"))
        assertTrue(ContentType.parse("a/b").match("*/*; *=*"))
    }

    @Test
    fun testContentTypeConst() {
        assertTrue { ContentType.Application.FormUrlEncoded.match(ContentType.Application.FormUrlEncoded) }
        assertFalse(ContentType.Application.Json.match(ContentType.Application.FormUrlEncoded))
    }
}