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
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OidcTokenRefreshCacheTest {

    @Test
    fun `completed refreshes are eagerly pruned when cache exceeds max size`() = runTest {
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
            val provider = OidcProvider(
                name = "auth0",
                client = client,
                config = OidcProviderConfig("auth0", OidcToken::class).apply {
                    testIssuer()
                    oauth {
                        clientId = "client-id"
                        clientSecret = "client-secret"
                    }
                    validate()
                },
            )
            provider.updateMetadata(openIdProviderMetadata)

            val cacheMaxSize = tokenRefreshCacheMaxSize()
            repeat(cacheMaxSize + 1) { index ->
                val result = provider.refreshToken("refresh-token-$index")
                assertEquals("access-token", result.accessToken)
            }

            assertTrue(provider.tokenRefreshCacheSize() <= cacheMaxSize)
        }
    }

    private fun tokenRefreshCacheMaxSize(): Int {
        val field = Class.forName(
            "io.ktor.server.auth.oidc.OidcProviderKt"
        ).getDeclaredField("TokenRefreshCacheMaxSize")
        field.isAccessible = true
        return field.getInt(null)
    }

    private fun OidcProvider<*>.tokenRefreshCacheSize(): Int {
        val field = OidcProvider::class.java.getDeclaredField("tokenRefreshes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(this) as ConcurrentHashMap<String, *>
        return cache.size
    }
}
