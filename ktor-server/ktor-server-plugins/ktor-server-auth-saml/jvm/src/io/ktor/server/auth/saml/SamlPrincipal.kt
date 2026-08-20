/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.saml.saml2.core.*

/**
 * Represents an unverified SAML credential extracted from a SAML response.
 *
 * @property response The SAML response containing the assertion
 * @property assertion The SAML assertion (maybe decrypted)
 */
public class SamlCredential(
    public val response: Response,
    public val assertion: Assertion
) {

    /**
     * The subject's NameID from the SAML assertion.
     * This is typically the unique identifier for the user.
     */
    public val nameId: String? get() = assertion.subject?.nameID?.value

    /**
     * Gets the first value of the specified attribute, or null if not present.
     *
     * @param name The attribute name
     * @return The first value of the attribute, or null
     */
    public fun getAttribute(name: String): String? {
        return assertion.attributeStatements
            .flatMap { it.attributes }
            .find { it.name == name }
            ?.attributeValues?.firstOrNull()
            ?.dom?.textContent
    }

    /**
     * Gets all values of the specified attribute.
     *
     * @param name The attribute name
     * @return List of attribute values, or empty list if not present
     */
    public fun getAttributeValues(name: String): List<String> {
        return assertion.attributeStatements
            .flatMap { it.attributes }
            .filter { it.name == name }
            .flatMap { attribute ->
                attribute.attributeValues.mapNotNull { it.dom?.textContent }
            }
    }

    /**
     * Checks if the assertion contains the specified attribute.
     *
     * @param name The attribute name
     * @return true if the attribute is present
     */
    public fun hasAttribute(name: String): Boolean {
        return assertion.attributeStatements
            .flatMap { it.attributes }
            .any { it.name == name }
    }

    /**
     * Map of all SAML attributes from the assertion's AttributeStatement.
     * Keys are attribute names, values are lists of attribute values.
     */
    public val attributes: Map<String, List<String>> by lazy {
        assertion.buildAttributesMap()
    }
}

/**
 * Represents an authenticated SAML principal.
 *
 * This principal is created after successful SAML assertion validation and contains
 * the verified user identity and attributes from the Identity Provider (IdP).
 *
 * @property assertion The validated SAML assertion
 * @property nameId The subject's NameID (user identifier) from the assertion
 * @property sessionIndex The session index for Single Logout (SLO) support
 * @property attributes Map of SAML attributes from the AttributeStatement
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.SamlPrincipal)
 */
public class SamlPrincipal(
    public val assertion: Assertion
) {

    /**
     * The subject's NameID from the SAML assertion.
     * This is typically the unique identifier for the authenticated user.
     */
    public val nameId: String = requireNotNull(assertion.subject?.nameID?.value) {
        "SAML assertion must contain a NameID"
    }

    /**
     * The session index from the AuthnStatement, if present.
     * This is used for Single Logout (SLO) to identify the session at the IdP.
     */
    public val sessionIndex: String? = assertion.authnStatements.firstOrNull()?.sessionIndex

    /**
     * Map of SAML attributes from the assertion's AttributeStatement.
     * Keys are attribute names, values are lists of attribute values.
     */
    public val attributes: Map<String, List<String>> by lazy {
        assertion.buildAttributesMap()
    }

    /**
     * Gets the first value of the specified attribute, or null if not present.
     *
     * @param name The attribute name
     * @return The first value of the attribute, or null
     */
    public fun getAttribute(name: String): String? = attributes[name]?.firstOrNull()

    /**
     * Gets all values of the specified attribute.
     *
     * @param name The attribute name
     * @return List of attribute values, or empty list if not present
     */
    public fun getAttributeValues(name: String): List<String> = attributes[name] ?: emptyList()

    /**
     * Checks if the assertion contains the specified attribute.
     *
     * @param name The attribute name
     * @return true if the attribute is present
     */
    public fun hasAttribute(name: String): Boolean = attributes.containsKey(name)
}

private fun Assertion.buildAttributesMap(): Map<String, List<String>> = buildMap {
    attributeStatements.forEach { attributeStatement ->
        attributeStatement.attributes.forEach { attribute ->
            val name = attribute.name ?: return@forEach
            val values = attribute.attributeValues.mapNotNull { attributeValue ->
                attributeValue.dom?.textContent
            }
            if (values.isNotEmpty()) {
                put(name, this[name].orEmpty() + values)
            }
        }
    }
}
