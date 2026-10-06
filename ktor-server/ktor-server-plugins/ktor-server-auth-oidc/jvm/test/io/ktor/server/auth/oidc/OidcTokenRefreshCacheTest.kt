/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.oidc.utils.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OidcTokenRefreshCacheTest {

    @Test
    fun `completed refreshes are eagerly pruned when cache exceeds max size`() = runTest {
        val cacheMaxSize = 4
        HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(discoveryJson)
            }
            engine {
                addHandler {
                    respond(
                        content = """{"access_token":"access-token","token_type":"Bearer"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }.use { client ->
            val provider = tokenRefreshProvider(
                client = client,
                tokenRefreshCacheMaxSize = cacheMaxSize,
            )
            repeat(cacheMaxSize + 1) { index ->
                val result = provider.refreshToken("refresh-token-$index")
                assertEquals("access-token", result.accessToken)
            }

            assertTrue(provider.tokenRefreshes.size <= cacheMaxSize)
        }
    }

    @Test
    fun `zero ttl removes completed refresh entries immediately`() = runTest {
        HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(discoveryJson)
            }
            engine {
                addHandler {
                    respond(
                        content = """{"access_token":"access-token","token_type":"Bearer"}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }.use { client ->
            val provider = tokenRefreshProvider(
                client = client,
                tokenRefreshCacheTtl = Duration.ZERO,
            )
            val result = provider.refreshToken("refresh-token")
            assertEquals("access-token", result.accessToken)
            assertEquals(0, provider.tokenRefreshes.size)
        }
    }

    private fun tokenRefreshProvider(
        client: HttpClient,
        tokenRefreshCacheTtl: Duration = 1.seconds,
        tokenRefreshCacheMaxSize: Int = 1024,
    ): OidcProvider {
        val provider = OidcProvider(
            name = "auth0",
            client = client,
            config = OidcProviderConfig("auth0").apply {
                testIssuer()
                this.tokenRefreshCacheTtl = tokenRefreshCacheTtl
                this.tokenRefreshCacheMaxSize = tokenRefreshCacheMaxSize
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
                validate()
            },
        )
        provider.updateMetadata(openIdProviderMetadata)
        return provider
    }
}
