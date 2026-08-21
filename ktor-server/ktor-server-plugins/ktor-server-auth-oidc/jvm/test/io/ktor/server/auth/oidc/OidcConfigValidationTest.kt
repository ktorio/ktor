/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import ch.qos.logback.classic.Level
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import kotlin.test.*

class OidcConfigValidationTest {

    @Test
    fun `route uri configs reject query parameters`() {
        val failure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                loginUri = {
                    path("auth0", "login")
                    parameters.append("debug", "true")
                }
            }
        }

        assertContains(failure.message.orEmpty(), "query parameters")
    }

    @Test
    fun `bearer and oauth configs validate required security settings`() {
        val bearerFailure = assertProviderValidationFails { bearer() }
        assertContains(bearerFailure.message.orEmpty(), "bearer")
        assertContains(bearerFailure.message.orEmpty(), "audience")

        val scopeFailure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                scopes = listOf("profile")
            }
        }
        assertContains(scopeFailure.message.orEmpty(), "openid")
    }

    @Test
    fun `jwkProviderFactory cannot be combined with cache or rate-limit config`() {
        val configurations: List<Pair<String, OidcJwtConfig.() -> Unit>> = listOf(
            "jwkCache" to { jwkCache() },
            "disableJwkCache" to { disableJwkCache() },
            "jwkRateLimit" to { jwkRateLimit() },
            "disableJwkRateLimit" to { disableJwkRateLimit() },
        )

        configurations.forEach { (name, configureJwt) ->
            val failure = assertProviderValidationFails {
                jwt {
                    jwkProviderFactory = { error("not used") }
                    configureJwt()
                }
            }
            assertContains(failure.message.orEmpty(), "jwkProviderFactory", message = name)
            assertContains(failure.message.orEmpty(), "jwkCache or jwkRateLimit", message = name)
        }
    }

    @Test
    fun `static metadata validates issuer and required endpoints`() {
        val issuerFailure = assertProviderValidationFails {
            metadata = testOpenIdProviderMetadata(issuer = "$ISSUER_URL/")
        }
        assertContains(issuerFailure.message.orEmpty(), "issuer mismatch")

        val missingFields = listOf(
            "jwks_uri" to testOpenIdProviderMetadata(issuer = ISSUER_URL, jwksUri = " "),
            "authorization_endpoint" to testOpenIdProviderMetadata(issuer = ISSUER_URL, authorizationEndpoint = " "),
            "token_endpoint" to testOpenIdProviderMetadata(issuer = ISSUER_URL, tokenEndpoint = " "),
        )

        missingFields.forEach { (field, metadata) ->
            val failure = assertProviderValidationFails {
                this.metadata = metadata
            }
            assertContains(failure.message.orEmpty(), field)
        }
    }

    @Test
    fun `oauth resource indicators are supported`() = testApplication {
        application {
            val oidc = install(Oidc)
            oidc.identityProvider("auth0") {
                testIssuer()
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    scopes = listOf("openid", "profile")
                    resourceIndicators = listOf("https://api.example.com", "https://mcp.example.com")
                }
            }
        }

        val response = noRedirectsClient().get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, response.status)
        val resources = Url(response.headers[HttpHeaders.Location].orEmpty()).parameters.getAll("resource").orEmpty()
        assertContains(resources, "https://api.example.com")
        assertContains(resources, "https://mcp.example.com")
    }

    @Test
    fun `oauth requires openid scope`() {
        val failure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                scopes = listOf("profile")
                sessions()
            }
        }

        assertContains(failure.message.orEmpty(), "openid")
    }

    @Test
    fun `logout and refresh require sessions`() {
        val logoutFailure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                disableSessions()
                logout("/custom/logout")
            }
        }
        assertContains(logoutFailure.message.orEmpty(), "sessions")

        val refreshFailure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                disableSessions()
                refresh("/custom/refresh")
            }
        }
        assertContains(refreshFailure.message.orEmpty(), "sessions")
    }

    @Test
    fun `sessionless oauth requires onAuthenticated`() {
        val failure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                disableSessions()
            }
        }
        assertContains(failure.message.orEmpty(), "onAuthenticated")
    }

    @Test
    fun `bearer token source defaults to authorization header unless customized`() {
        OidcProviderConfig("default").apply {
            bearer()
            assertNull(bearerConfig!!.tokenExtractor)
        }
        OidcProviderConfig("session").apply {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                sessions()
                assertNotNull(sessionConfig!!.csrfConfigurer)
            }
            bearer()
            assertNull(bearerConfig!!.tokenExtractor)
        }
        OidcProviderConfig("custom").apply {
            bearer {
                tokenExtractor = { call.request.headers["X-Token"] }
            }
            assertNotNull(bearerConfig!!.tokenExtractor)
        }
    }

    @Test
    fun `session storage memory warning is emitted only for production memory storage`() {
        val customStorage = object : SessionStorage {
            override suspend fun write(id: String, value: String) {}

            override suspend fun invalidate(id: String) {}

            override suspend fun read(id: String): String = error("not used")
        }

        assertSessionStorageWarning(providerName = "auth0", configure = {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                stateEncryptionKey = testStateEncryptionKey()
                sessions()
            }
        }) { events ->
            assertTrue(events.any { it.formattedMessage.contains("SessionStorageMemory") })
        }
        assertSessionStorageWarning(providerName = "custom-storage", configure = {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                stateEncryptionKey = testStateEncryptionKey()
                sessions {
                    storage = customStorage
                }
            }
        }) { events ->
            assertTrue(events.none { it.formattedMessage.contains("SessionStorageMemory") })
        }
    }

    @Test
    fun `oauth without state encryption key logs a warning and generates an ephemeral key`() {
        listOf(true, false).forEach { developmentMode ->
            captureProviderLogs("auth0", Level.WARN).use { logs ->
                testApplication {
                    serverConfig {
                        this.developmentMode = developmentMode
                    }
                    application {
                        val oidc = install(Oidc)
                        oidc.identityProvider("auth0") {
                            testIssuer()
                            oauth {
                                clientId = "client-id"
                                clientSecret = "client-secret"
                            }
                        }
                    }
                    startApplication()
                }

                val warnings = logs.events.filter { it.formattedMessage.contains("stateEncryptionKey") }
                assertEquals(1, warnings.size, "developmentMode=$developmentMode")
                assertContains(warnings.single().formattedMessage, "ephemeral key")
                assertContains(warnings.single().formattedMessage, "shared stateEncryptionKey")
            }
        }
    }

    private fun assertProviderValidationFails(
        configure: OidcProviderConfig.() -> Unit,
    ): Throwable = assertFailsWith<IllegalArgumentException> {
        testApplication {
            application {
                val oidc = install(Oidc)
                oidc.identityProvider("auth0") {
                    testIssuer()
                    configure()
                }
            }
            startApplication()
        }
    }

    private fun assertSessionStorageWarning(
        providerName: String,
        configure: OidcProviderConfig.() -> Unit,
        assertions: (List<ch.qos.logback.classic.spi.ILoggingEvent>) -> Unit,
    ) {
        captureProviderLogs(providerName, Level.WARN).use { logs ->
            testApplication {
                serverConfig {
                    developmentMode = false
                }
                application {
                    val oidc = install(Oidc)
                    oidc.identityProvider(providerName) {
                        testIssuer()
                        configure()
                    }
                }
                startApplication()
            }
            assertions(logs.events)
        }
    }

    private fun testStateEncryptionKey(): OidcStateEncryptionKey =
        OidcStateEncryptionKey.of(ByteArray(OidcStateEncryptionKey.KEY_SIZE) { it.toByte() })
}
