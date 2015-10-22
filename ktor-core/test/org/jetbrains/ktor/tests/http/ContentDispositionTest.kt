package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class ContentDispositionTest {
    @Test
    fun testRenderSimple() {
        assertEquals("file", ContentDisposition.File.toString())
    }

    @Test
    fun testRenderSimpleWithParameter() {
        assertEquals("file; k=v", ContentDisposition.File.withParameter("k", "v").toString())
    }

    @Test
    fun testRenderSimpleWithMultipleParameters() {
        assertEquals("file; k1=v1; k2=v2", ContentDisposition.File.withParameters(ValuesMap.build { append("k1", "v1"); append("k2", "v2") }).toString())
    }

    @Test
    fun testRenderEscaped() {
        assertEquals("file; k=\"v,v\"", ContentDisposition.File.withParameter("k", "v,v").toString())
        assertEquals("file; k=\"v,v\"; k2=\"=\"", ContentDisposition.File.withParameter("k", "v,v").withParameter("k2", "=").toString())
        assertEquals("file; k=\"v,v\"; k2=v2", ContentDisposition.File.withParameter("k", "v,v").withParameter("k2", "v2").toString())
    }
}