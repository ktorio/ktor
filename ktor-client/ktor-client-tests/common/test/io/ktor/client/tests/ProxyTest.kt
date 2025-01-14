/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class ProxyResponse(val status: String)

class ProxyTest : ClientLoader() {

    @Test
    fun testHttpProxy() = clientTests(except("Js", "web:CIO")) {
        config {
            engine {
                proxy = ProxyBuilder.http(TCP_SERVER)
            }
        }

        test { client ->
            val response = client.get("http://google.com").body<String>()
            assertEquals("proxy", response)
        }
    }

    @Test
    fun testProxyWithSerialization() = clientTests(except("Js", "web:CIO")) {
        config {
            engine {
                proxy = ProxyBuilder.http(TCP_SERVER)
            }

            install(ContentNegotiation) { json() }
        }

        test { client ->
            val response = client.get("http://google.com/json").body<ProxyResponse>()
            val expected = ProxyResponse("ok")

            assertEquals(expected, response)
        }
    }
}
