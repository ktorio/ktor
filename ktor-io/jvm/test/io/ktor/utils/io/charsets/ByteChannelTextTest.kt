/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.charsets

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class ByteChannelTextTest {

    @Test
    fun testReadUtf8LineThrowTooLongLine() = runBlocking<Unit> {
        val line100 = (0..99).joinToString("")
        val channel = ByteChannel()
        channel.writeStringUtf8(line100)
        channel.close()

        assertFailsWith<TooLongLineException> {
            channel.readUTF8Line(50)
        }
    }
}
