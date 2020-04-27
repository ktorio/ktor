/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.tests

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*
import io.ktor.test.dispatcher.*

class ByteChannelBuildersTest {
    @Test
    fun testWriterCancelledByChannel() = testSuspend {
        val context = Job()
        val scope = CoroutineScope(context)

        val task = scope.writer {
            val data = ByteArray(8 * 1024)
            while (true) {
                channel.writeFully(data)
            }
        }

        context.complete()
        task.channel.cancel()
        context.join()
        assertTrue(task.isCancelled)
        assertTrue(context.isCompleted)
    }
}
