/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.features

import io.ktor.client.engine.mock.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class CookiesTest {

    @Test
    fun testCompatibility() = clientTest(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    assertEquals("*/*", request.headers[HttpHeaders.Accept])
                    val rawCookies = request.headers[HttpHeaders.Cookie]!!
                    assertEquals(1, request.headers.getAll(HttpHeaders.Cookie)?.size!!)
                    assertEquals("first=\"1,2,3,4\";second=abc;", rawCookies)

                    respondOk()
                }
            }

            install(HttpCookies) {
                default {
                    runBlocking {
                        addCookie("//localhost", Cookie("first", "1,2,3,4"))
                        addCookie("http://localhost", Cookie("second", "abc"))
                    }
                }
            }
        }

        test { client ->
            client.get<HttpStatement>().execute { }
        }
    }

    @Test
    fun testAllowedCharacters() = clientTest(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    assertEquals("myServer=value:value;", request.headers[HttpHeaders.Cookie])
                    respondOk()
                }
            }

            install(HttpCookies) {
                default {
                    runBlocking {
                        addCookie("http://localhost", Cookie("myServer", "value:value"))
                    }
                }
            }
        }

        test { client ->
            client.get<String>()
        }
    }
}
