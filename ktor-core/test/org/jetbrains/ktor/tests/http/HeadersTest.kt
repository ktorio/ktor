package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.junit.*
import kotlin.test.*

public class HeadersTest {
    @Test
    fun `parse simple accept header`() {
        val items = "audio/basic".orderedContentTypeHeaderItems()
        assertEquals(1, items.count())
        assertEquals("audio/basic", items.single().value)
    }

    @Test
    fun `parse accept header with fallback`() {
        val items = "audio/*; q=0.2, audio/basic".orderedContentTypeHeaderItems()
        assertEquals(2, items.count())
        assertEquals("audio/basic", items[0].value)
        assertEquals("audio/*", items[1].value)
    }

    @Test
    fun `parse accept header with preference`() {
        val items = "text/plain; q=0.5, text/html,text/x-dvi; q=0.8, text/x-c".orderedContentTypeHeaderItems()
        assertEquals(4, items.count())

        assertEquals(HeaderItem("text/html"), items[0])
        assertEquals(HeaderItem("text/x-c"), items[1])
        assertEquals(HeaderItem("text/x-dvi", listOf(HeaderItemParam("q", "0.8"))), items[2])
        assertEquals(HeaderItem("text/plain", listOf(HeaderItemParam("q", "0.5"))), items[3])
    }

    @Test
    fun `parse accept header with extra parameters`() {
        val items = "text/*, text/html, text/html;level=1, */*".orderedContentTypeHeaderItems()
        assertEquals(4, items.count())
        assertEquals(HeaderItem("text/html", listOf(HeaderItemParam("level", "1"))), items[0])
        assertEquals(HeaderItem("text/html"), items[1])
        assertEquals(HeaderItem("text/*"), items[2])
        assertEquals(HeaderItem("*/*"), items[3])
    }

    @Test
    fun `parse accept header with extra parameters and fallback`() {
        val items = "text/*;q=0.3, text/html;q=0.7, text/html;level=1,text/html;level=2;q=0.4, */*;q=0.5".orderedContentTypeHeaderItems()
        assertEquals(5, items.count())

        assertEquals(HeaderItem("text/html", listOf(HeaderItemParam("level", "1"))), items[0])
        assertEquals(HeaderItem("text/html", listOf(HeaderItemParam("q", "0.7"))), items[1])
        assertEquals(HeaderItem("*/*", listOf(HeaderItemParam("q", "0.5"))), items[2])
        assertEquals(HeaderItem("text/html", listOf(HeaderItemParam("level", "2"), HeaderItemParam("q", "0.4"))), items[3])
        assertEquals(HeaderItem("text/*", listOf(HeaderItemParam("q", "0.3"))), items[4])
    }
}