/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.test.*
import io.ktor.utils.io.*
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.fail

class ByteReadChannelReadTest {

    @Test
    fun testReadAvailableFromEmpty() = runTest {
        val channel = ByteReadChannel(ByteArray(0))

        channel.read(0) { _: ByteBuffer ->
            fail()
        }
    }
}
