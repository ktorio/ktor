/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpMethodTest : ClientLoader() {

    @Test
    fun `all HTTP methods should be supported`() = clientTests {
        test { client ->
            for (method in client.supportedMethods()) {
                val response = client.request("$TEST_SERVER/echo/method") { this.method = method }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(method.value, response.headers["Http-Method"])
            }
        }
    }
}

private val allMethods = HttpMethod.DefaultMethods + HttpMethod.Trace

private fun HttpClient.supportedMethods(): List<HttpMethod> = when (engineName) {
    // PATCH is not supported by HttpURLConnection
    // https://bugs.openjdk.org/browse/JDK-7016595
    "AndroidClientEngine" -> allMethods - HttpMethod.Patch
    // Js engine throws: TypeError: 'TRACE' HTTP method is unsupported.
    "JsClientEngine" -> allMethods - HttpMethod.Trace
    else -> allMethods
}

private val HttpClient.engineName get() = engine::class.simpleName
private val HttpMethod.Companion.Trace get() = HttpMethod("TRACE")
