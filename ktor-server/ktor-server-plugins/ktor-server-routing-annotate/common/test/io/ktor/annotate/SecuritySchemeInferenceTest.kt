/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.client.*
import io.ktor.openapi.*
import io.ktor.server.auth.*
import io.ktor.server.auth.apikey.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlin.test.*

class SecuritySchemeInferenceTest {

    @Test
    fun testInferBasicAuthenticationProvider() = testApplication {
        install(Authentication) {
            basic("basic-auth") {}
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme = schemes["basic-auth"] as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme.type)
        assertEquals("basic", scheme.scheme)
        assertEquals("HTTP Basic Authentication", scheme.description)
        assertNull(scheme.bearerFormat)
    }

    @Test
    fun testInferMultipleBasicProviders() = testApplication {
        install(Authentication) {
            basic("admin-basic") {}
            basic("user-basic", "Custom Basic Auth Description") {}
        }
        startApplication()

        val schemes = application.findSecuritySchemes()

        assertNotNull(schemes)
        assertEquals(2, schemes.size)

        val adminScheme = schemes["admin-basic"] as HttpSecurityScheme
        val userScheme = schemes["user-basic"] as HttpSecurityScheme

        assertEquals("HTTP Basic Authentication", adminScheme.description)
        assertEquals("Custom Basic Auth Description", userScheme.description)
        assertEquals("basic", adminScheme.scheme)
        assertEquals("basic", userScheme.scheme)
    }

    @Test
    fun testInferBearerAuthenticationProvider() = testApplication {
        install(Authentication) {
            bearer("bearer-auth") {}
            bearer("multi-scheme-bearer", "Custom description") {
                authSchemes("Token", "ApiKey")
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme1 = schemes["bearer-auth"] as HttpSecurityScheme
        assertEquals(SecuritySchemeType.HTTP, scheme1.type)
        assertEquals("bearer", scheme1.scheme)
        assertEquals("HTTP Bearer Authentication", scheme1.description)

        val scheme2 = schemes["multi-scheme-bearer"] as HttpSecurityScheme

        // Inference creates a standard-bearer scheme regardless of custom schemes
        assertEquals("bearer", scheme2.scheme)
        assertEquals("Custom description", scheme2.description)
    }

    @Test
    fun testInferApiKeyAuthenticationProvider() = testApplication {
        install(Authentication) {
            apiKey("api-key-header") {
                validate { UserIdPrincipal("user") }
            }
            apiKey("api-key-custom", "Custom API Key Description") {
                headerName = "X-Custom-Key"
                validate { UserIdPrincipal("user") }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)
        assertEquals(2, schemes.size)

        val headerScheme = schemes["api-key-header"] as ApiKeySecurityScheme
        assertEquals(SecuritySchemeType.API_KEY, headerScheme.type)
        assertEquals("X-Api-Key", headerScheme.name)
        assertEquals(SecuritySchemeIn.HEADER, headerScheme.`in`)
        assertEquals("API Key Authentication", headerScheme.description)

        val customScheme = schemes["api-key-custom"] as ApiKeySecurityScheme
        assertEquals(SecuritySchemeType.API_KEY, customScheme.type)
        assertEquals("X-Custom-Key", customScheme.name)
        assertEquals(SecuritySchemeIn.HEADER, customScheme.`in`)
        assertEquals("Custom API Key Description", customScheme.description)
    }

    @Test
    fun testInferFormAuthenticationProvider() = testApplication {
        install(Authentication) {
            form("default-form") {
                validate { UserIdPrincipal(it.name) }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNull(schemes)
    }

    @Serializable
    data class UserSession(val userId: String)

    @Serializable
    data class ApiSession(val token: String)

    @Test
    fun testInferSessionAuth() = testApplication {
        install(Sessions) {
            cookie<UserSession>("user_session") {
                cookie.path = "/"
            }
            header<ApiSession>("X-Session-Token") {}
        }
        install(Authentication) {
            session<UserSession>(name = "cookie-auth") {
                validate { it }
            }
            session<ApiSession>("header-session") {
                validate { it }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val cookieScheme = schemes["cookie-auth"] as ApiKeySecurityScheme
        assertEquals(SecuritySchemeType.API_KEY, cookieScheme.type)
        assertEquals("user_session", cookieScheme.name)
        assertEquals(SecuritySchemeIn.COOKIE, cookieScheme.`in`)
        assertEquals(
            "Session-based Authentication",
            cookieScheme.description
        )

        val headerScheme = schemes["header-session"] as ApiKeySecurityScheme

        assertEquals(SecuritySchemeType.API_KEY, headerScheme.type)
        assertEquals("X-Session-Token", headerScheme.name)
        assertEquals(SecuritySchemeIn.HEADER, headerScheme.`in`)
        assertEquals(
            "Session-based Authentication",
            headerScheme.description
        )
    }

    @Test
    fun testInferSessionAuthWithDescription() = testApplication {
        install(Sessions) {
            cookie<UserSession>("user_session") {
                cookie.path = "/"
            }
            header<ApiSession>("some-header")
        }
        install(Authentication) {
            session(
                name = "header-session-empty-description",
                description = "",
                kClass = UserSession::class
            ) {
                validate { it }
            }
            session(
                name = "header-session-custom-description",
                description = "Custom Description",
                kClass = ApiSession::class
            ) {
                validate { it }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val headerScheme1 = schemes["header-session-empty-description"] as ApiKeySecurityScheme
        assertEquals("user_session", headerScheme1.name)
        assertEquals(SecuritySchemeIn.COOKIE, headerScheme1.`in`)
        assertEquals("", headerScheme1.description)

        val headerScheme2 = schemes["header-session-custom-description"] as ApiKeySecurityScheme
        assertEquals("some-header", headerScheme2.name)
        assertEquals(SecuritySchemeIn.HEADER, headerScheme2.`in`)
    }

    @Test
    fun testInferOAuthAuthenticationProvider() = testApplication {
        install(Authentication) {
            oauth("oauth-no-desc") {
                urlProvider = { "http://localhost/callback" }
                settings = OAuthServerSettings.OAuth2ServerSettings(
                    name = "test",
                    authorizeUrl = "https://auth.example.com/authorize",
                    accessTokenUrl = "https://auth.example.com/token",
                    clientId = "client",
                    clientSecret = "secret",
                    defaultScopes = listOf("profile", "email")
                )
                client = HttpClient()
            }

            oauth("oauth-custom", "OAuth2 Authentication for Social Login") {
                urlProvider = { "http://localhost/callback" }
                settings = OAuthServerSettings.OAuth2ServerSettings(
                    name = "social",
                    authorizeUrl = "https://social.example.com/authorize",
                    accessTokenUrl = "https://social.example.com/token",
                    refreshUrl = "https://social.example.com/refresh",
                    clientId = "client",
                    clientSecret = "secret",
                    defaultScopes = listOf("profile", "email"),
                    defaultScopeDescriptions = mapOf("profile" to "User profile", "email" to "User email")
                )
                client = HttpClient()
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme = schemes["oauth-no-desc"] as OAuth2SecurityScheme
        assertEquals(SecuritySchemeType.OAUTH2, scheme.type)
        assertEquals("OAuth2 Authentication", scheme.description)

        val flow1 = scheme.flows?.authorizationCode!!
        assertEquals("https://auth.example.com/token", flow1.tokenUrl)
        assertEquals("https://auth.example.com/authorize", flow1.authorizationUrl)
        assertEquals(null, flow1.refreshUrl)

        assertEquals(2, flow1.scopes?.size)
        assertEquals("OAuth2 scope", flow1.scopes?.get("profile"))
        assertEquals("OAuth2 scope", scheme.flows?.authorizationCode?.scopes?.get("email"))

        val scheme2 = schemes["oauth-custom"] as OAuth2SecurityScheme
        assertEquals(SecuritySchemeType.OAUTH2, scheme2.type)
        assertEquals("OAuth2 Authentication for Social Login", scheme2.description)

        val flow2 = scheme2.flows?.authorizationCode!!
        assertEquals("https://social.example.com/token", flow2.tokenUrl)
        assertEquals("https://social.example.com/authorize", flow2.authorizationUrl)
        assertEquals("https://social.example.com/refresh", flow2.refreshUrl)

        assertEquals(2, flow2.scopes?.size)
        assertEquals("User profile", flow2.scopes?.get("profile"))
        assertEquals("User email", flow2.scopes?.get("email"))
    }

    @Test
    fun testRegisterOpenIdConnectSecurityScheme() = testApplication {
        application {
            registerOpenIdConnectSecurityScheme(
                name = "openid-connect",
                openIdConnectUrl = "https://example.com/.well-known/openid-configuration"
            )
            registerOpenIdConnectSecurityScheme(
                name = "openid-connect-with-desc",
                openIdConnectUrl = "https://auth.example.com/.well-known/openid-configuration",
                description = "Custom OpenID Connect Authentication"
            )
        }
        startApplication()

        val schemes = application.findSecuritySchemes()

        assertNotNull(schemes)
        assertEquals(2, schemes.size)

        val scheme1 = schemes["openid-connect"] as OpenIdConnectSecurityScheme
        assertEquals(SecuritySchemeType.OPEN_ID_CONNECT, scheme1.type)
        assertEquals("https://example.com/.well-known/openid-configuration", scheme1.openIdConnectUrl)
        assertEquals("OpenID Connect Authentication", scheme1.description)

        val scheme2 = schemes["openid-connect-with-desc"] as OpenIdConnectSecurityScheme
        assertEquals(SecuritySchemeType.OPEN_ID_CONNECT, scheme2.type)
        assertEquals("https://auth.example.com/.well-known/openid-configuration", scheme2.openIdConnectUrl)
        assertEquals("Custom OpenID Connect Authentication", scheme2.description)
    }

    @Test
    fun testInferMixedAuthenticationProviders() = testApplication {
        install(Sessions) {
            cookie<UserSession>("session")
        }
        install(Authentication) {
            basic("basic") {}
            bearer("bearer") {}
            form("form") {} // shouldn't be inferred
            session<UserSession>("session") {
                validate { it }
            }
        }
        startApplication()

        val schemes = application.findSecuritySchemes()

        assertNotNull(schemes)
        assertEquals(3, schemes.size)

        assertTrue(schemes["basic"] is HttpSecurityScheme)
        assertEquals("basic", (schemes["basic"] as HttpSecurityScheme).scheme)

        assertTrue(schemes["bearer"] is HttpSecurityScheme)
        assertEquals("bearer", (schemes["bearer"] as HttpSecurityScheme).scheme)

        assertTrue(schemes["session"] is ApiKeySecurityScheme)
        assertEquals(SecuritySchemeIn.COOKIE, (schemes["session"] as ApiKeySecurityScheme).`in`)
    }

    @Test
    fun testInferDefaultProviderName() = testApplication {
        install(Authentication) {
            basic(name = null) {}
        }
        startApplication()

        val schemes = application.findSecuritySchemes()
        assertNotNull(schemes)

        val scheme = schemes[AuthenticationRouteSelector.DEFAULT_NAME] as HttpSecurityScheme
        assertEquals("basic", scheme.scheme)
    }

    @Test
    fun testManualRegistrationOverridesInference() = testApplication {
        application {
            registerBearerAuthSecurityScheme("bearer-manual", "Custom Bearer", "JWT")
        }

        install(Authentication) {
            bearer("bearer-manual") {}
            basic("basic-inferred") {}
        }
        startApplication()

        val schemes = application.findSecuritySchemes()

        assertNotNull(schemes)
        assertEquals(2, schemes.size)

        val bearerScheme = schemes["bearer-manual"] as HttpSecurityScheme
        assertEquals("Custom Bearer", bearerScheme.description)
        assertEquals("JWT", bearerScheme.bearerFormat)

        val basicScheme = schemes["basic-inferred"] as HttpSecurityScheme
        assertEquals("HTTP Basic Authentication", basicScheme.description)
    }

    @Test
    fun testNoAuthenticationPluginInstalled() = testApplication {
        val schemes = application.findSecuritySchemes()
        assertNull(schemes)
    }

    @Test
    fun testEmptyAuthenticationPlugin() = testApplication {
        install(Authentication)
        startApplication()
        val schemes = application.findSecuritySchemes()
        assertNull(schemes)
    }

    @Test
    fun testOnlyManualRegistrationNoAuthPlugin() = testApplication {
        application {
            registerBasicAuthSecurityScheme("manual-only")
            registerBearerAuthSecurityScheme("bearer-manual")
        }
        startApplication()

        val schemes = application.findSecuritySchemes()

        assertNotNull(schemes)
        assertEquals(2, schemes.size)
        assertTrue(schemes.containsKey("manual-only"))
        assertTrue(schemes.containsKey("bearer-manual"))
    }

    @Test
    fun testInferenceWithDynamicProvider() = testApplication {
        install(Authentication) {
            provider("custom-dynamic") {
                authenticate { }
            }
        }
        startApplication()
        val schemes = application.findSecuritySchemes()
        // Dynamic providers cannot be inferred automatically
        assertNull(schemes)
    }

    @Test
    fun testDisabledCaching() = testApplication {
        val schemes = application.findSecuritySchemes(useCache = true)
        assertNull(schemes)

        application.registerBasicAuthSecurityScheme("basic-auth")

        val cachedSchemes = application.findSecuritySchemes(useCache = true)
        assertNull(cachedSchemes)

        val schemes2 = application.findSecuritySchemes(useCache = false)
        assertNotNull(schemes2)
        assertEquals(1, schemes2.size)
        assertTrue(schemes2.containsKey("basic-auth"))
    }
}
