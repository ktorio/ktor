/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlin.test.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SamlPrincipalTest {

    @Test
    fun `test SamlCredential construction`() {
        val expectedNameId = "user123@example.com"
        val expectedSessionIndex = "session-12345"
        val assertion = SamlTestUtils.createTestAssertion(
            nameId = expectedNameId,
            sessionIndex = expectedSessionIndex
        )

        val response = SamlTestUtils.createTestResponse(assertion)
        val credential = SamlCredential(response, assertion)
        val principal = SamlPrincipal(assertion)

        assertEquals(expectedNameId, principal.nameId)
        assertEquals(response, credential.response)
        assertEquals(assertion, credential.assertion)
        assertEquals(expectedSessionIndex, principal.sessionIndex)
    }

    @Test
    fun `test SamlPrincipal throws exception when NameID is missing`() {
        val assertion = SamlTestUtils.createTestAssertion().apply {
            subject?.nameID = null // Remove the NameID
        }
        assertFailsWith<IllegalArgumentException> {
            SamlPrincipal(assertion)
        }
    }

    @Test
    fun `test SamlPrincipal sessionIndex is null when AuthnStatement is missing`() {
        val assertion = SamlTestUtils.createTestAssertion(sessionIndex = null)
        val principal = SamlPrincipal(assertion)

        assertNull(principal.sessionIndex)
    }

    @Test
    fun `test SamlPrincipal missing attribute`() {
        val assertion = SamlTestUtils.createTestAssertion()
        val principal = SamlPrincipal(assertion)

        assertFalse(principal.hasAttribute("nonexistent"))
        assertNull(principal.getAttribute("nonexistent"))

        val values = principal.getAttributeValues("nonexistent")
        assertTrue(values.isEmpty())
    }
}
