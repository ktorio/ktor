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
        val httpMethods = HttpMethod.DefaultMethods + HttpMethod("TRACE")

        test { client ->
            for (method in httpMethods) {
                if (!client.supportsMethod(method)) continue

                val response = client.request("$TEST_SERVER/echo/method") { this.method = method }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(method.value, response.headers["Http-Method"])
            }
        }
    }
}

private fun HttpClient.supportsMethod(method: HttpMethod): Boolean = when (engine::class.simpleName) {
    // https://bugs.openjdk.org/browse/JDK-7016595
    "AndroidClientEngine" -> method != HttpMethod.Patch
    else -> true
}
