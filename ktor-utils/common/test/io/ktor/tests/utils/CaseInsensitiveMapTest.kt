package io.ktor.util

import io.ktor.util.*
import kotlin.test.*

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

        map["Content-Type"] = "text/plain"
        assertEquals("text/plain", map["Content-Type"])
    }
}