/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.test.dispatcher.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

class ResponseValidatorTest {
    @Serializable
    data class ResponseJsonData(val status: Int, val message: String)

    @Test
    fun testValidationWithContentNegotiationPlugin() = testSuspend {
        val client = HttpClient(
            MockEngine { request ->
                val bodyBytes = (request.body as OutgoingContent.ByteArrayContent).bytes()
                respondOk(String(bodyBytes))
            }
        ) {
            install(ContentNegotiation) { json() }
            HttpResponseValidator {
                validateResponse {
                    val body: String = it.body()
                    val response = Json.decodeFromString<ResponseJsonData>(body)
                    if (response.status != 200) {
                        throw ClientRequestException(it, response.message)
                    }
                }
            }
        }

        val response: String = client.get {
            setBody(ResponseJsonData(200, "OK"))
            contentType(ContentType.Application.Json)
        }.body()

        assertEquals("{\"status\":200,\"message\":\"OK\"}", response)
        assertEquals(ResponseJsonData(200,"OK"), Json.decodeFromString(response))
    }
}
