/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.android

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlin.test.*

class AndroidEngineTest {
    @Test
    fun testHttpClient() = {
        val client = HttpClient(Android {

        })
    }

    @Test
    fun testUrlConnection() = {
        val client = HttpClient(Android {

        })
    }
}
