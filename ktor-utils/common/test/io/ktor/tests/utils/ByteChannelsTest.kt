/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class ByteChannelsTest {
    @Test
    fun testSplitOnMainAndDependant() = testSuspend {
        val job = Job()
        val channel = ByteChannel(true)
        val (dependant, main) = channel.splitOnDependantAndMain(CoroutineScope(job))

        assertEquals(0, dependant.availableForRead)
        assertEquals(0, main.availableForRead)

        with (channel) {
            writeByte(42)
            close()
        }

        assertEquals(0, dependant.availableForRead)
        assertEquals(42, main.readByte())
        delay(1000)

        assertEquals(1, dependant.availableForRead)
        assertEquals(42, dependant.readByte())
    }
}
