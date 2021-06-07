/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.cio

import io.ktor.http.cio.internals.*
import java.util.zip.*
import kotlin.random.*
import kotlin.test.*
import kotlin.test.Test

class WebSocketDeflateTest {
    private val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    private val inflater = Inflater(true)

    @Test
    fun testDeflateInflateEmpty() {
        val data = byteArrayOf()
        val deflated = deflater.deflateFully(data)
        val inflated = inflater.inflateFully(deflated)

        assertTrue { data.contentEquals(inflated) }
    }

    @Test
    fun testDeflateInflateForRandomData() {
        repeat(1000) {
            val data = Random.nextBytes(it * 10)
            val deflated = deflater.deflateFully(data)
            val inflated = inflater.inflateFully(deflated)

            assertTrue {
                data.contentEquals(inflated)
            }
        }
    }
}
