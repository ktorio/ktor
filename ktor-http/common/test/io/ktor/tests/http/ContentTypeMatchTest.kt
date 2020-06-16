/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class ContentTypeMatchTest {
    @Test
    fun testTypeAndSubtype() {
        assertTrue { ContentType.parse("*").match("text/plain") }
        assertTrue { ContentType.parse("* ").match("text/plain") }
        assertTrue { ContentType.parse("*/*").match("text/plain") }
        assertTrue { ContentType.parse("*/ *").match("text/plain") }
        assertTrue { ContentType.parse("*/plain").match("text/plain") }
        assertTrue { ContentType.parse("* /plain").match("text/plain") }
        assertTrue { ContentType.parse("*/plain").match("text/PLAIN") }
        assertTrue { ContentType.parse("text/*").match("text/plain") }
        assertTrue { ContentType.parse("text/plain").match("text/plain") }
        assertTrue { ContentType.parse("TEXT/plain").match("text/plain") }

        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("text/") }
        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("/plain") }
        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("foobar") }
        assertFailsWith<BadContentTypeFormatException> { ContentType.parse("foo/bar/baz") }

        assertFalse(ContentType.parse("text/plain").match("image/plain"))
        assertFalse(ContentType.parse("text/plain").match("text/xml"))
    }

    @Test
    fun testParametersConstants() {
        assertTrue { ContentType.parse("*/*; a=1").match("a/b; a=1") }
        assertTrue { ContentType.parse("*/*; a=1").match("a/b; A=1") }
        assertFalse(ContentType.parse("*/*; a=2").match("a/b"))
        assertFalse(ContentType.parse("*/*; a=2").match("a/b; a=1"))
        assertFalse(ContentType.parse("*/*; a=2").match("a/b; A=1"))
    }

    @Test
    fun testParametersWithSubtype() {
        assertTrue { ContentType.parse("a/b").match("a/b; a=1") }
        assertTrue { ContentType.parse("a/b; a=XYZ").match("a/b; a=xyz") }
    }

    @Test
    fun testParametersValueWildcard() {
        assertTrue(ContentType.parse("*/*; a=*").match("a/b; a=1"))
        assertFalse(ContentType.parse("*/*; a=*").match("a/b; b=1"))
    }

    @Test
    fun testParametersNameWildcard() {
        assertTrue(ContentType.parse("*/*; *=1").match("a/b; a=1"))
        assertTrue(ContentType.parse("*/*; *=x").match("a/b; a=X"))
        assertFalse(ContentType.parse("*/*; *=1").match("a/b; a=2"))
        assertFalse(ContentType.parse("*/*; *=x").match("a/b; a=y"))
    }

    @Test
    fun testParametersAllWildcard() {
        assertTrue(ContentType.parse("*/*; *=*").match("a/b; a=2"))
        assertTrue(ContentType.parse("*/*; *=*").match("a/b"))
    }

    @Test
    fun testContentTypeConst() {
        assertTrue { ContentType.Application.FormUrlEncoded.match(ContentType.Application.FormUrlEncoded) }
        assertFalse(ContentType.Application.Json.match(ContentType.Application.FormUrlEncoded))
    }
}
