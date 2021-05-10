/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth

import io.ktor.client.features.auth.providers.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.*
import kotlin.test.*

class TokenHolderTest {

    @Test
    fun testSetTokenCalledOnce() = testSuspend {
        val holder = AuthTokenHolder<BearerTokens> { TODO() }

        val monitor = Job()
        var firstExecuted = false
        var secondExecuted = false
        val first = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                println(1)
                firstExecuted = true
                monitor.join()
                BearerTokens("1", "2")
            }
        }

        val second = GlobalScope.launch(Dispatchers.Unconfined) {
            holder.setToken {
                println(2)
                secondExecuted = true
                BearerTokens("1", "2")
            }
        }

        println(3)
        monitor.complete()
        println(4)
        first.join()
        println(5)
        second.join()
        println(6)

        assertTrue(firstExecuted)
        assertFalse(secondExecuted)
    }
}
