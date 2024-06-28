/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class UrlTest {

    @Test
    fun `get url`() = runTest {
        val httpClient = HttpClient(OkHttp)
        val response = httpClient
            .get("https://github.com/typicode/json-server")
            .bodyAsText()
        println(response)
    }

}
