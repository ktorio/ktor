/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

class BuildersTest {

    @Test
    fun testResolvingWsFunction() = runBlocking {
        try {
            HttpClient(CIO).ws("http://localhost") {}
        } catch (_: Throwable) {
            // no op
        }
    }
}
