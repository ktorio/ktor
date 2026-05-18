/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OidcDiscoveryTest {

    @Test
    fun `provider uses refreshed discovery metadata`() = testApplication {
        val discoveryRequests = AtomicInteger()
        val allowRefreshResponse = CompletableDeferred<Unit>()

        refreshingOpenIdProvider(discoveryRequests, allowRefreshResponse)

        val openIdClient = openIdHttpClient()
        lateinit var provider: OidcProvider<OidcPrincipal>
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = 10.milliseconds
                discoveryRefreshFailureDelay = 10.milliseconds
            }
            provider = oidc.provider("auth0") {
                issuer = ISSUER_URL
            }
        }

        startApplication()
        assertEquals("$ISSUER_URL/authorize-initial", provider.currentMetadata().authorizationEndpoint)

        allowRefreshResponse.complete(Unit)
        val refreshedAuthorizeEndpoint = withTimeout(5.seconds) {
            while (true) {
                val endpoint = provider.currentMetadata().authorizationEndpoint
                if (endpoint.endsWith("/authorize-refreshed")) {
                    return@withTimeout endpoint
                }
                delay(10.milliseconds)
            }
        }
        assertEquals("$ISSUER_URL/authorize-refreshed", refreshedAuthorizeEndpoint)
    }

    @Test
    fun `failed periodic refresh raises event and keeps stale metadata`() = testApplication {
        val discoveryRequests = AtomicInteger()
        val refreshFailed = CompletableDeferred<OidcMetadataRefreshFailure>()

        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/.well-known/openid-configuration") {
                        val requestNumber = discoveryRequests.incrementAndGet()
                        val metadata = if (requestNumber == 1) {
                            OpenIdProviderMetadata(
                                issuer = ISSUER_URL,
                                authorizationEndpoint = "$ISSUER_URL/authorize-initial",
                                tokenEndpoint = "$ISSUER_URL/token",
                                jwksUri = "$ISSUER_URL/jwks",
                            )
                        } else {
                            OpenIdProviderMetadata(
                                issuer = "$ISSUER_URL/",
                                authorizationEndpoint = "$ISSUER_URL/authorize-invalid",
                                tokenEndpoint = "$ISSUER_URL/token",
                                jwksUri = "$ISSUER_URL/jwks",
                            )
                        }
                        call.respondText(openIdTestJson.encodeToString(metadata), ContentType.Application.Json)
                    }
                }
            }
        }

        val openIdClient = openIdHttpClient()
        lateinit var provider: OidcProvider<OidcPrincipal>
        application {
            monitor.subscribe(OidcMetadataRefreshFailed) { failure ->
                refreshFailed.complete(failure)
            }
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = 10.milliseconds
                discoveryRefreshFailureDelay = 10.milliseconds
            }
            provider = oidc.provider("auth0") {
                issuer = ISSUER_URL
            }
        }

        startApplication()
        assertEquals("$ISSUER_URL/authorize-initial", provider.currentMetadata().authorizationEndpoint)

        val failure = withTimeout(5.seconds) { refreshFailed.await() }
        assertSame(provider, failure.provider)
        assertEquals("auth0", failure.provider.name)
        assertEquals(ISSUER_URL, failure.provider.issuer)
        assertEquals("$ISSUER_URL/authorize-initial", provider.currentMetadata().authorizationEndpoint)
        assertEquals(1, failure.consecutiveFailures)
        assertIs<IllegalArgumentException>(failure.cause)
        assertContains(failure.cause.message.orEmpty(), "issuer mismatch")
    }

    @Test
    fun `initial discovery retries non issuer failure during registration`() = testApplication {
        val discoveryRequests = AtomicInteger()

        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/.well-known/openid-configuration") {
                        if (discoveryRequests.incrementAndGet() == 1) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "temporarily unavailable")
                        } else {
                            call.respondText(
                                openIdTestJson.encodeToString(openIdProviderMetadata),
                                ContentType.Application.Json,
                            )
                        }
                    }
                }
            }
        }

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
                initialDiscoveryAttempts = 2
                initialDiscoveryRetryDelay = ZERO
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
            }
        }

        startApplication()
        assertEquals(2, discoveryRequests.get())
    }

    @Test
    fun `initial discovery throws after configured attempts fail`() {
        val discoveryRequests = AtomicInteger()

        val failure = assertFailsWith<DiscoveryException> {
            testApplication {
                externalServices {
                    hosts(ISSUER_URL) {
                        routing {
                            get("/.well-known/openid-configuration") {
                                discoveryRequests.incrementAndGet()
                                call.respond(HttpStatusCode.ServiceUnavailable, "temporarily unavailable")
                            }
                        }
                    }
                }

                val openIdClient = openIdHttpClient()
                application {
                    val oidc = openIdConnect {
                        httpClient = openIdClient
                        discoveryRefreshInterval = ZERO
                        initialDiscoveryAttempts = 2
                        initialDiscoveryRetryDelay = ZERO
                    }
                    oidc.provider("auth0") {
                        issuer = ISSUER_URL
                    }
                }
                startApplication()
            }
        }

        assertContains(failure.message.orEmpty(), "after 2 attempt")
        assertEquals(2, discoveryRequests.get())
    }

    @Test
    fun `failed initial discovery releases reserved provider name and issuer`() = testApplication {
        val discoveryRequests = AtomicInteger()

        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/.well-known/openid-configuration") {
                        if (discoveryRequests.incrementAndGet() == 1) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "temporarily unavailable")
                        } else {
                            call.respondText(
                                openIdTestJson.encodeToString(openIdProviderMetadata),
                                ContentType.Application.Json,
                            )
                        }
                    }
                }
            }
        }

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
                initialDiscoveryAttempts = 1
                initialDiscoveryRetryDelay = ZERO
            }
            assertFailsWith<DiscoveryException> {
                oidc.provider("auth0") {
                    issuer = ISSUER_URL
                }
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
            }
        }

        startApplication()
        assertEquals(2, discoveryRequests.get())
    }

    @Test
    fun `initial discovery rejects non exact issuer without retrying`() {
        val discoveryRequests = AtomicInteger()

        val failure = assertFailsWith<IllegalArgumentException> {
            testApplication {
                externalServices {
                    hosts(ISSUER_URL) {
                        routing {
                            get("/.well-known/openid-configuration") {
                                discoveryRequests.incrementAndGet()
                                call.respondText(
                                    openIdTestJson.encodeToString(
                                        OpenIdProviderMetadata(
                                            issuer = "$ISSUER_URL/",
                                            authorizationEndpoint = "$ISSUER_URL/authorize",
                                            tokenEndpoint = "$ISSUER_URL/token",
                                            jwksUri = "$ISSUER_URL/jwks",
                                        )
                                    ),
                                    ContentType.Application.Json,
                                )
                            }
                        }
                    }
                }

                val openIdClient = openIdHttpClient()
                application {
                    val oidc = openIdConnect {
                        httpClient = openIdClient
                        discoveryRefreshInterval = ZERO
                        initialDiscoveryAttempts = 2
                        initialDiscoveryRetryDelay = ZERO
                    }
                    oidc.provider("auth0") {
                        issuer = ISSUER_URL
                    }
                }
                startApplication()
            }
        }

        assertContains(
            failure.message.orEmpty(),
            "OpenID issuer mismatch: expected exactly $ISSUER_URL, got $ISSUER_URL/"
        )
        assertEquals(1, discoveryRequests.get())
    }

    private fun TestApplicationBuilder.refreshingOpenIdProvider(
        discoveryRequests: AtomicInteger,
        allowRefreshResponse: CompletableDeferred<Unit>,
    ) {
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    refreshingDiscoveryEndpoint(discoveryRequests, allowRefreshResponse)
                }
            }
        }
    }

    private fun Route.refreshingDiscoveryEndpoint(
        discoveryRequests: AtomicInteger,
        allowRefreshResponse: CompletableDeferred<Unit>,
    ) {
        get("/.well-known/openid-configuration") {
            val requestNumber = discoveryRequests.incrementAndGet()
            val authorizationEndpoint = if (requestNumber == 1) {
                "$ISSUER_URL/authorize-initial"
            } else {
                allowRefreshResponse.await()
                "$ISSUER_URL/authorize-refreshed"
            }

            call.respondText(
                openIdTestJson.encodeToString(
                    OpenIdProviderMetadata(
                        issuer = ISSUER_URL,
                        authorizationEndpoint = authorizationEndpoint,
                        tokenEndpoint = "$ISSUER_URL/token",
                        jwksUri = "$ISSUER_URL/jwks",
                    )
                ),
                ContentType.Application.Json,
            )
        }
    }
}
