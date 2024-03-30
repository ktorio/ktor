/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugin.tracing

import android.app.*
import android.content.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import org.mockito.Mockito.*
import kotlin.test.*

class AndroidEngineTest {
    @Test
    fun testHttpClient() = {
        val activity = createTestAndroidActivity()
        with(activity) {
            val client = HttpClient(Stetho(CIO))
            val page = runBlocking { client.get("http://www.google.com").bodyAsText() }
            assertNotNull(page)
        }
    }
}
