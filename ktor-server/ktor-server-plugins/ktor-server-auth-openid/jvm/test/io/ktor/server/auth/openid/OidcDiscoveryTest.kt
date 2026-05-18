/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
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
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = 10.milliseconds
                discoveryRefreshFailureDelay = 10.milliseconds
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    scopes = listOf("openid", "profile")
                }
            }
        }

        val browser = noRedirectsClient()
        val initialLogin = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, initialLogin.status)
        val initialAuthorizeUrl = Url(assertNotNull(initialLogin.headers[HttpHeaders.Location]))
        assertEquals("/authorize-initial", initialAuthorizeUrl.encodedPath)
        assertEquals("client-id", initialAuthorizeUrl.parameters["client_id"])
        assertEquals("openid profile", initialAuthorizeUrl.parameters["scope"])

        allowRefreshResponse.complete(Unit)
        val refreshedAuthorizePath = withTimeout(5.seconds) {
            while (true) {
                val refreshedLogin = browser.get("/oidc/auth0/login")
                assertEquals(HttpStatusCode.Found, refreshedLogin.status)
                val refreshedAuthorizeUrl = Url(assertNotNull(refreshedLogin.headers[HttpHeaders.Location]))
                if (refreshedAuthorizeUrl.encodedPath == "/authorize-refreshed") {
                    return@withTimeout refreshedAuthorizeUrl.encodedPath
                }
                delay(10.milliseconds)
            }
        }
        assertEquals("/authorize-refreshed", refreshedAuthorizePath)
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
        application {
            monitor.subscribe(OidcMetadataRefreshFailed) { failure ->
                refreshFailed.complete(failure)
            }
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = 10.milliseconds
                discoveryRefreshFailureDelay = 10.milliseconds
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }
        }

        val browser = noRedirectsClient()
        val initialLogin = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, initialLogin.status)
        assertEquals(
            "/authorize-initial",
            Url(assertNotNull(initialLogin.headers[HttpHeaders.Location])).encodedPath,
        )

        val failure = withTimeout(5.seconds) { refreshFailed.await() }
        val provider = failure.provider
        assertEquals("auth0", provider.name)
        assertEquals(ISSUER_URL, provider.issuer)
        assertEquals("$ISSUER_URL/authorize-initial", provider.currentMetadata().authorizationEndpoint)
        assertEquals(1, failure.consecutiveFailures)
        assertIs<IllegalArgumentException>(failure.cause)
        assertContains(failure.cause.message.orEmpty(), "issuer mismatch")

        val staleLogin = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, staleLogin.status)
        assertEquals(
            "/authorize-initial",
            Url(assertNotNull(staleLogin.headers[HttpHeaders.Location])).encodedPath,
        )
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
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }
        }

        val login = noRedirectsClient().get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
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
                        accessToken {
                            audiences = setOf("api")
                        }
                        bearer()
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
                    oauth {
                        clientId = "client-id"
                        clientSecret = "client-secret"
                    }
                }
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }
        }

        val login = noRedirectsClient().get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
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
                        accessToken {
                            audiences = setOf("api")
                        }
                        bearer()
                    }
                }
                startApplication()
            }
        }

        assertContains(
            failure.message.orEmpty(),
            "OpenID issuer mismatch: expected exactly $ISSUER_URL, got $ISSUER_URL/"
        )
        assertIs<IllegalArgumentException>(failure)
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
