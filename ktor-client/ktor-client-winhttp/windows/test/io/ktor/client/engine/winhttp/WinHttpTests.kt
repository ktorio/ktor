/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlin.test.*

class WinHttpTests {
    @Test
    fun testDownload() {
        val client = HttpClient(WinHttp)

        val responseText = runBlocking {
            client.get("https://google.com").body<String>()
        }

        assertTrue { responseText.isNotEmpty() }
    }
}
