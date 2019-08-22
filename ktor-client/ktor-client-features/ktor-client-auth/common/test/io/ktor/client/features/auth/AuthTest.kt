/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth

import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class AuthTest : ClientLoader() {
    @Test
    fun testDigestAuth() = clientTests(listOf("js")) {
        config {
            install(Auth) {
                digest {
                    username = "MyName"
                    password = "Circle Of Life"
                    realm = "testrealm@host.com"
                }
            }
        }
        test { client ->
            client.get<HttpResponse>("$TEST_SERVER/auth/digest").use {
                assertTrue(it.status.isSuccess())
            }
        }
    }

    @Test
    fun testBasicAuth(): Unit = clientTests(listOf("js")) {
        config {
            install(Auth) {
                basic {
                    username = "MyUser"
                    password = "1234"
                }
            }
        }

        test { client ->
            client.get<String>("$TEST_SERVER/auth/basic-fixed")
        }
    }

    @Test
    fun testBasicAuthWithoutNegotiation(): Unit = clientTests {
        config {
            install(Auth) {
                basic {
                    username = "MyUser"
                    password = "1234"

                    sendWithoutRequest = true
                }
            }
        }

        test { client ->
            client.get<String>("$TEST_SERVER/auth/basic-fixed")
        }
    }
}
