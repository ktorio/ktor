/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.server.auth.*

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
