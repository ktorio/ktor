/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapConfigDecodeTest {

    @Test
    fun testSimpleTypes() {
        val config = MapApplicationConfig()
        config.put("x.string" , "test")
        config.put("x.int" , "42")
        config.put("x.long" , "1234567890")
        config.put("x.float" , "3.14")
        config.put("x.double" , "3.14159")
        config.put("x.boolean" , "true")
        config.put("x.char" , "x")
        config.put("x.byte" , "127")
        config.put("x.short" , "32000")

        assertEquals("test", config.property("x.string").getAs())
        assertEquals(42, config.property("x.int").getAs())
        assertEquals(1234567890, config.property("x.long").getAs())
        assertEquals(3.14f, config.property("x.float").getAs())
        assertEquals(3.14159, config.property("x.double").getAs())
        assertTrue(config.property("x.boolean").getAs())
        assertEquals('x', config.property("x.char").getAs())
        assertEquals(127.toByte(), config.property("x.byte").getAs())
        assertEquals(32000.toShort(), config.property("x.short").getAs())

        val x = config.property("x").getAs<MapDecoderTest.SimpleConfig>()
        assertEquals("test", x.string)
        assertEquals(42, x.int)
        assertEquals(1234567890, x.long)
        assertEquals(3.14f, x.float)
        assertEquals(3.14159, x.double)
        assertTrue(x.boolean)
        assertEquals('x', x.char)
        assertEquals(127.toByte(), x.byte)
        assertEquals(32000.toShort(), x.short)
    }
}
