/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth

import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class AuthTest : ClientLoader() {
    @Test
    fun testDigestAuth() = clientTests(listOf("Js")) {
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
            client.get<HttpStatement>("$TEST_SERVER/auth/digest").execute {
                assertTrue(it.status.isSuccess())
            }
        }
    }

    @Test
    fun testBasicAuth() = clientTests(listOf("Js")) {
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
    fun testBasicAuthWithoutNegotiation() = clientTests {
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

    @Test
    fun testUnauthorizedBasicAuth() = clientTests(listOf("Js")) {
        config {
            install(Auth) {
                basic {
                    username = "usr"
                    password = "pw"
                }
            }
            expectSuccess = false
        }

        test { client ->
            client.get<HttpStatement>("$TEST_SERVER/auth/unauthorized").execute { response ->
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun testUnauthorizedBearerAuthWithInvalidAccessAndRefreshTokensAsNulls() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokensFun = { null }
                    loadTokensFun = { null }
                }
            }
        }

        test { client ->
            client.get<HttpStatement>("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.Unauthorized, it.status)
            }
        }
    }

    @Test
    fun testUnauthorizedBearerAuthWithInvalidAccessAndRefreshTokens() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokensFun = { BearerTokens("invalid", "refresh") }
                    loadTokensFun = { BearerTokens("invalid", "refresh") }
                }
            }
        }

        test { client ->

            client.get<HttpStatement>("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.Unauthorized, it.status)
            }
        }
    }

    @Test
    // The return of refreshTokenFun is null, cause it should not be called at all, if loadTokensFun returns valid tokens
    fun testUnauthorizedBearerAuthWithValidAccessTokenAndInvalidRefreshToken() = clientTests {
        config {
            install(Auth) {
                bearer {
                    refreshTokensFun = { null }
                    loadTokensFun = { BearerTokens("valid", "refresh") }
                }
            }
        }

        test { client ->
            client.get<HttpStatement>("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
    }

    @Test
    // If loadTokensFun returns "invalid" tokens, than refreshTokenFun should refresh tokens and repeat the call
    fun testUnauthorizedBearerAuthWithInvalidAccessTokenAndValidRefreshToken() = clientTests(listOf()) {
        config {
            install(Auth) {
                bearer {
                    refreshTokensFun = { BearerTokens("valid", "refresh") }
                    loadTokensFun = { BearerTokens("invalid", "refresh") }
                    realm = "TestServer"
                }
            }
        }

        test { client ->
            client.get<HttpStatement>("$TEST_SERVER/auth/bearer/test-refresh").execute {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
    }

}
