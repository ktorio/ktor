/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*

/**
 * Installs SAML 2.0 authentication.
 *
 * SAML (Security Assertion Markup Language) 2.0 is an XML-based standard for exchanging
 * authentication and authorization data between parties. This plugin implements the
 * Web Browser SSO Profile for both Service Provider-initiated and Identity Provider-initiated authentication flows.
 *
 * ## Example Usage
 *
 * ```kotlin
 * install(Authentication) {
 *     saml("saml-auth") {
 *         // Service Provider configuration
 *         sp = SamlSpMetadata {
 *             spEntityId = "https://myapp.example.com/saml/metadata"
 *             acsUrl = "https://myapp.example.com/saml/acs"
 *             signingCredential = SamlCrypto.loadCredential(
 *                 keystorePath = "/path/to/keystore.jks",
 *                 keystorePassword = "example_pass",
 *                 keyAlias = "sp-key",
 *                 keyPassword = "example_pass"
 *             )
 *         }
 *
 *         // Identity Provider metadata
 *         idp = parseSamlIdpMetadata(idpMetadataXml)
 *
 *         // Validation logic
 *         validate { credential ->
 *             val nameId = credential.nameId
 *             val email = credential.getAttribute("email")
 *             if (email != null) {
 *                 SamlPrincipal(credential.assertion)
 *             } else {
 *                 null
 *             }
 *         }
 *     }
 * }
 *
 * routing {
 *     authenticate("saml-auth") {
 *         get("/profile") {
 *             val principal = call.principal<SamlPrincipal>()!!
 *             call.respondText("Hello ${principal.nameId}")
 *         }
 *     }
 * }
 * ```
 *
 * ## Security Features
 *
 * This implementation follows OWASP SAML Security Cheat Sheet recommendations:
 * - **XXE Protection**: Prevents XML External Entity attacks
 * - **XSW Protection**: Prevents XML Signature Wrapping
 * - **Replay Protection**: Tracks processed assertion IDs to prevent replay attacks
 * - **Signature Verification**: Validates XML signatures on assertions using IdP certificates
 * - **Timestamp Validation**: Checks NotBefore/NotOnOrAfter with configurable clock skew
 * - **Audience Restriction**: Ensures assertions are intended for this Service Provider
 *
 * @param name Optional name for this authentication provider
 * @param configure Configuration block for SAML authentication
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.saml)
 */
public fun AuthenticationConfig.saml(
    name: String? = null,
    configure: SamlConfig.() -> Unit
): Unit = saml(name, description = null, configure)

/**
 * Installs SAML 2.0 authentication with a custom description.
 *
 * @param name Optional name for this authentication provider
 * @param description Optional description of the provider
 * @param configure Configuration block for SAML authentication
 *
 * @see saml
 */
public fun AuthenticationConfig.saml(
    name: String? = null,
    description: String? = null,
    configure: SamlConfig.() -> Unit
) {
    LibSaml.ensureInitialized()
    val provider = SamlConfig(name, description)
        .apply(configure)
        .let { SamlAuthenticationProvider(it) }

    register(provider)
}

/**
 * Initiates SP-initiated SAML Single Logout.
 *
 * @param principal The authenticated SAML principal containing NameID and session info
 * @param spMetadata The Service Provider metadata containing the entity ID and signing credential
 * @param idpSloUrl The IdP's SLO URL
 * @param relayState Optional URL to redirect to after logout completes
 * @param signatureAlgorithm The signature algorithm to use for signing the LogoutRequest
 * @return [SamlRedirectResult] containing the request ID and redirect URL to the IdP
 */
public fun ApplicationCall.samlLogout(
    principal: SamlPrincipal,
    spMetadata: SamlSpMetadata,
    idpSloUrl: String,
    relayState: String? = null,
    signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256
): SamlRedirectResult = samlLogout(
    nameId = principal.nameId,
    idpSloUrl = idpSloUrl,
    spMetadata = spMetadata,
    sessionIndex = principal.sessionIndex,
    relayState = relayState,
    signatureAlgorithm = signatureAlgorithm
)

/**
 * Initiates SP-initiated SAML Single Logout with explicit NameID.
 *
 * @param nameId The NameID of the user to log out
 * @param idpSloUrl The IdP's SLO URL
 * @param spMetadata The Service Provider metadata containing the entity ID and signing credential
 * @param sessionIndex The session index from the AuthnStatement (optional but recommended)
 * @param relayState Optional URL to redirect to after logout completes
 * @param signatureAlgorithm The signature algorithm to use for signing the LogoutRequest
 * @return [SamlRedirectResult] containing the request ID and redirect URL to the IdP
 */
public fun ApplicationCall.samlLogout(
    nameId: String,
    idpSloUrl: String,
    spMetadata: SamlSpMetadata,
    sessionIndex: String?,
    relayState: String? = null,
    signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.RSA_SHA256
): SamlRedirectResult {
    LibSaml.ensureInitialized()

    val spEntityId = spMetadata.spEntityId
    require(!spEntityId.isNullOrBlank()) { "spEntityId must not be blank for logout" }
    require(nameId.isNotBlank()) { "nameId must not be blank for logout" }
    require(idpSloUrl.isNotBlank()) { "idpSloUrl must not be blank for logout" }

    val result = buildLogoutRequestRedirect(
        nameId = nameId,
        idpSloUrl = idpSloUrl,
        relayState = relayState,
        spEntityId = spEntityId,
        sessionIndex = sessionIndex,
        signingCredential = spMetadata.signingCredential,
        signatureAlgorithm = signatureAlgorithm
    )

    // Store the logout request ID in the session for InResponseTo validation
    val currentSession = checkNotNull(sessions.get<SamlSession>()) {
        "No current session found. Did you forget to call authenticate() or sessions.install()?"
    }
    val newSession = SamlSession(
        requestId = currentSession.requestId,
        logoutRequestId = result.messageId
    )
    sessions.set(newSession)

    return result
}
