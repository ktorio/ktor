/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class UnconfinedTests {

    @Test
    fun testReaderPropagation() {
        var resumed = false
        var startFlush = false

        val origin = ByteChannel(false)
        GlobalScope.reader(Dispatchers.Unconfined, origin) {
            origin.readSuspendableSession {
                await()
                assertTrue(startFlush)
                resumed = true
            }
        }.channel

        val channel = GlobalScope.reader(Dispatchers.Unconfined, autoFlush = true) {
            channel.copyTo(origin, Long.MAX_VALUE)
            origin.flush()
        }.channel

        val task = suspend {
            val packet = BytePacketBuilder()
            packet.writeFully(ByteArray(119))
            channel.writePacket(packet.build())
            startFlush = true
            channel.flush()
            channel.close()
            Unit
        }

        SingleStepContinuation().assertSuccess(task)
        assertTrue(resumed)
    }
}

private class SingleStepContinuation : Continuation<Unit> {
    lateinit var result: Any
    override val context: CoroutineContext get() = Dispatchers.Unconfined

    override fun resumeWith(result: Result<Unit>) {
        this.result = result
    }

    fun assertSuccess(task: suspend () -> Unit) {
        task.startCoroutine(this)
        assertEquals(Result.success(Unit), result)
    }
}
