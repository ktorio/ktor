/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO

class OidcEnvironmentConfigTest {

    @Test
    fun `environment provider is ignored until registered in code`() = testApplication {
        val discoveryRequests = AtomicInteger()

        environment {
            config = oidcEnvironmentConfig()
        }
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/.well-known/openid-configuration") {
                        discoveryRequests.incrementAndGet()
                        call.respondText(
                            openIdTestJson.encodeToString(openIdProviderMetadata),
                            ContentType.Application.Json
                        )
                    }
                }
            }
        }

        val openIdClient = openIdHttpClient()
        application {
            openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
        }

        startApplication()
        assertEquals(0, discoveryRequests.get())
    }

    @Test
    fun `environment provider issuer can be extended in code`() = testApplication {
        environment {
            config = oidcEnvironmentConfig()
        }
        openIdProvider()

        val openIdClient = openIdHttpClient()
        lateinit var provider: OidcProvider
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            provider = oidc.provider("auth0") {}
        }

        startApplication()
        assertEquals(ISSUER_URL, provider.issuer)
        assertEquals(openIdProviderMetadata.issuer, provider.currentMetadata().issuer)
        assertEquals(openIdProviderMetadata.authorizationEndpoint, provider.currentMetadata().authorizationEndpoint)
    }

    @Test
    fun `failed provider configuration does not consume environment provider config`() = testApplication {
        val discoveryRequests = AtomicInteger()

        environment {
            config = oidcEnvironmentConfig()
        }
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    get("/.well-known/openid-configuration") {
                        discoveryRequests.incrementAndGet()
                        call.respondText(
                            openIdTestJson.encodeToString(openIdProviderMetadata),
                            ContentType.Application.Json
                        )
                    }
                }
            }
        }

        val openIdClient = openIdHttpClient()
        lateinit var provider: OidcProvider
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }

            val failure = assertFailsWith<IllegalArgumentException> {
                oidc.provider("auth0") {
                    issuer = " "
                }
            }
            assertContains(failure.message.orEmpty(), "issuer")

            provider = oidc.provider("auth0") {}
        }

        startApplication()
        assertEquals(ISSUER_URL, provider.issuer)
        assertEquals(1, discoveryRequests.get())
    }

    private fun oidcEnvironmentConfig(
        providerName: String = "auth0",
    ): MapApplicationConfig =
        MapApplicationConfig("ktor.openid.$providerName.issuer" to ISSUER_URL)
}
