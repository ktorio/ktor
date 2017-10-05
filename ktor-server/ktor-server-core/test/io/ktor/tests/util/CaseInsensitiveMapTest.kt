package io.ktor.tests.util

import io.ktor.http.*
import io.ktor.util.*
import org.junit.*
import kotlin.test.*

/**
 * Author: Sergey Mashkov
 */
class CaseInsensitiveMapTest {
    val map = CaseInsensitiveMap<String>()

    @Test
    fun smokeTest() {
        map["aA"] = "a"

        assertEquals("a", map["aA"])
        assertEquals("a", map["aa"])
        assertEquals("a", map["AA"])
        assertEquals("a", map["Aa"])

        map["someLongKey-547575458645845158458614864864586458651861861861986148"] = "8"
        assertEquals("8", map["someLongKey-547575458645845158458614864864586458651861861861986148"])
        assertEquals("8", map["somelongkey-547575458645845158458614864864586458651861861861986148"])
        assertEquals("8", map["somelongKey-547575458645845158458614864864586458651861861861986148"])

        map[HttpHeaders.ContentType] = "text/plain"
        assertEquals("text/plain", map[HttpHeaders.ContentType])

    }
}