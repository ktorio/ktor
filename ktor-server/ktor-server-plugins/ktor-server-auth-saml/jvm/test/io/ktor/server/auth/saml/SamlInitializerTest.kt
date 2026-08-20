/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import java.io.*
import kotlin.test.*

class LibSamlTest {

    @Test
    fun `test ensureInitialized is idempotent`() {
        LibSaml.ensureInitialized()
        LibSaml.ensureInitialized()
        LibSaml.ensureInitialized()

        assertNotNull(LibSaml.parserPool)
    }

    @Test
    fun `test parser pool can parse valid XML`() {
        LibSaml.ensureInitialized()
        val validXml = """<?xml version="1.0"?>
            <root>
                <child>Test Content</child>
            </root>
        """.trimIndent()

        val document = LibSaml.parserPool.parse(ByteArrayInputStream(validXml.toByteArray()))

        assertNotNull(document)
        val rootElement = document.documentElement
        assertEquals("root", rootElement.nodeName)
    }

    @Test
    fun `test parser pool rejects DOCTYPE declarations (XXE protection)`() {
        LibSaml.ensureInitialized()

        val xmlWithDoctype = """<?xml version="1.0"?>
            <!DOCTYPE root [
                <!ELEMENT root ANY>
                <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <root>&xxe;</root>
        """.trimIndent()

        assertFailsWith<Exception> {
            LibSaml.parserPool.parse(ByteArrayInputStream(xmlWithDoctype.toByteArray()))
        }
    }

    @Test
    fun `test parser pool parses SAML-like XML structure`() {
        LibSaml.ensureInitialized()

        val samlLikeXml = """<?xml version="1.0"?>
            <saml2:Assertion xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" ID="_test" Version="2.0">
                <saml2:Issuer>test-issuer</saml2:Issuer>
                <saml2:Subject>
                    <saml2:NameID>test-user</saml2:NameID>
                </saml2:Subject>
            </saml2:Assertion>
        """.trimIndent()

        val document = LibSaml.parserPool.parse(ByteArrayInputStream(samlLikeXml.toByteArray()))

        assertNotNull(document)
        val rootElement = document.documentElement
        assertTrue(rootElement.nodeName.contains("Assertion"))
    }
}
