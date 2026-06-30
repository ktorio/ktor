/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import ch.qos.logback.classic.Level
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
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
        assertContains(bearerFailure.message.orEmpty(), "accessToken")
        assertContains(bearerFailure.message.orEmpty(), "audiences")

        val scopeFailure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                scopes = listOf("profile")
            }
        }
        assertContains(scopeFailure.message.orEmpty(), "openid")
        assertContains(scopeFailure.message.orEmpty(), "accessToken")

        val audienceFailure = assertProviderValidationFails {
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                idTokenAudience = " "
            }
        }
        assertContains(audienceFailure.message.orEmpty(), "idTokenAudience")
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
    fun `oauth access-token-only scopes and resource indicators are supported`() = testApplication {
        application {
            val oidc = openIdConnect { }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    scopes = listOf("profile")
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
    fun `session oauth requires openid when access token is configured`() {
        val failure = assertProviderValidationFails {
            accessToken {
                audiences = setOf("api")
            }
            sessions()
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                scopes = listOf("profile")
            }
        }

        assertContains(failure.message.orEmpty(), "openid")
        assertContains(failure.message.orEmpty(), "without sessions")
    }

    @Test
    fun `session config stores routes names storage and csrf settings`() {
        val customStorage = SessionStorageMemory()

        OidcProviderConfig("sessions", OidcToken::class).apply {
            assertNull(sessionConfig)
            sessions {
                refreshUri = { path("custom", "refresh") }
                logoutUri = { path("custom", "logout") }
                name = "CUSTOM"
                storage = customStorage
                disableCsrfProtection()
            }
            assertNotNull(sessionConfig!!.refreshUri)
            assertNotNull(sessionConfig!!.logoutUri)
            assertEquals("CUSTOM", sessionConfig!!.name)
            assertSame(customStorage, sessionConfig!!.storage)
            assertNull(sessionConfig!!.csrfConfigurer)
        }

        OidcProviderConfig("csrf", OidcToken::class).apply {
            sessions {
                csrfProtection {
                    allowOrigin("https://example.com")
                }
            }
            assertNotNull(sessionConfig!!.csrfConfigurer)
        }
    }

    @Test
    fun `bearer token source defaults to authorization header unless customized`() {
        OidcProviderConfig("default", OidcToken::class).apply {
            bearer()
            assertNull(bearerConfig!!.tokenExtractor)
        }
        OidcProviderConfig("session", OidcToken::class).apply {
            sessions()
            bearer()
            assertNull(bearerConfig!!.tokenExtractor)
            assertNotNull(sessionConfig!!.csrfConfigurer)
        }
        OidcProviderConfig("custom", OidcToken::class).apply {
            bearer {
                tokenExtractor = { call -> call.request.headers["X-Token"] }
            }
            assertNotNull(bearerConfig!!.tokenExtractor)
        }
    }

    @Test
    fun `session storage memory warning is emitted only for production memory storage`() {
        val customStorage = object : SessionStorage {
            override suspend fun write(id: String, value: String) {
            }

            override suspend fun invalidate(id: String) {
            }

            override suspend fun read(id: String): String = error("not used")
        }

        assertSessionStorageWarning(providerName = "auth0", configure = { sessions() }) { events ->
            assertTrue(events.any { it.formattedMessage.contains("SessionStorageMemory") })
        }
        assertSessionStorageWarning(providerName = "custom-storage", configure = {
            sessions {
                storage = customStorage
            }
        }) { events ->
            assertTrue(events.none { it.formattedMessage.contains("SessionStorageMemory") })
        }
    }

    @Test
    fun `production oauth requires state encryption key`() {
        val failure = assertFailsWith<IllegalStateException> {
            testApplication {
                serverConfig {
                    developmentMode = false
                }
                application {
                    val oidc = openIdConnect { }
                    oidc.provider("auth0") {
                        testIssuer()
                        oauth {
                            clientId = "client-id"
                            clientSecret = "client-secret"
                        }
                    }
                }
                startApplication()
            }
        }

        assertContains(failure.message.orEmpty(), "stateEncryptionKey")
        assertContains(failure.message.orEmpty(), "production")
    }

    @Test
    fun `development oauth without state encryption key logs one warning`() {
        captureProviderLogs("auth0", Level.WARN).use { logs ->
            testApplication {
                application {
                    val oidc = openIdConnect { }
                    oidc.provider("auth0") {
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
            assertEquals(1, warnings.size)
        }
    }

    private fun assertProviderValidationFails(
        configure: OidcProviderConfig<OidcToken>.() -> Unit,
    ): Throwable = assertFailsWith<IllegalArgumentException> {
        testApplication {
            application {
                val oidc = openIdConnect { }
                oidc.provider("auth0") {
                    testIssuer()
                    configure()
                }
            }
            startApplication()
        }
    }

    private fun assertSessionStorageWarning(
        providerName: String,
        configure: OidcProviderConfig<OidcToken>.() -> Unit,
        assertions: (List<ch.qos.logback.classic.spi.ILoggingEvent>) -> Unit,
    ) {
        captureProviderLogs(providerName, Level.WARN).use { logs ->
            testApplication {
                serverConfig {
                    developmentMode = false
                }
                application {
                    val oidc = openIdConnect { }
                    oidc.provider(providerName) {
                        testIssuer()
                        oauth {
                            clientId = "client-id"
                            clientSecret = "client-secret"
                            stateEncryptionKey = testStateEncryptionKey()
                        }
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
