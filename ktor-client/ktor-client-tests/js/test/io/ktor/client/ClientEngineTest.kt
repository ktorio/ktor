/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.js.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.*
import org.w3c.fetch.*
import kotlin.test.*

class ClientEngineTest : ClientLoader(timeoutSeconds = 5) {

    @Test
    fun requestCredentialsDefault() = testWithEngine(Js) {
        config {
            expectSuccess = false
        }
        test { client ->
            val setCookie =
                client.get<HttpResponse>(urlString = "$TEST_SERVER/cookies/httponly")
            with(setCookie) {
                assertEquals(HttpStatusCode.OK, status)
            }

            with(client.get<HttpResponse>(urlString = "$TEST_SERVER/cookies/httponly/test")) {
                assertEquals(HttpStatusCode.ExpectationFailed, status)
            }
        }
    }

    @Test
    fun requestCredentialsInclude() = testWithEngine(Js) {
        config {
            engine {
                if (PlatformUtils.IS_NODE) {
                    assertFailsWith<IllegalStateException> {
                        credentials = RequestCredentials.INCLUDE
                    }
                } else {
                    credentials = RequestCredentials.INCLUDE
                }
            }
        }
        if (PlatformUtils.IS_BROWSER) {
            test { client ->
                val setCookie =
                    client.get<HttpResponse>(urlString = "https://127.0.0.1:8089/cookies/httponly")
                with(setCookie) {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals(0, setCookie().size, "Set-Cookie header is not accessible from JS.")
                }

                with(client.get<HttpResponse>(urlString = "https://127.0.0.1:8089/cookies/httponly/test")) {
                    assertEquals(HttpStatusCode.OK, status)
                }
            }
        }
    }
}
