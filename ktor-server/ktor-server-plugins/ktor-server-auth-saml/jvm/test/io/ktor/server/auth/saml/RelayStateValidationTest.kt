/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlin.test.*

/**
 * Unit tests for RelayValidator to prevent open redirect attacks.
 */
class RelayStateValidationTest {

    @Test
    fun `relative paths with empty allowlist are accepted`() {
        val validator = RelayValidator(allowedRelayStateUrls = emptyList())

        // Basic relative paths with various components
        assertTrue(validator.validate("/dashboard"))
        assertTrue(validator.validate("/search?q=test&page=1"))
        assertTrue(validator.validate("/page#section"))
        assertTrue(validator.validate("/any/path"))
        assertTrue(validator.validate("/"))
    }

    @Test
    fun `dangerous URL patterns are blocked`() {
        val validator = RelayValidator(allowedRelayStateUrls = emptyList())

        // Scheme-relative, backslashes, and control characters
        assertFalse(validator.validate("//evil.com/phish"))
        assertFalse(validator.validate("/foo\\..\\bar"))
        assertFalse(validator.validate("/foo\u0000bar"))

        // Dangerous schemes
        assertFalse(validator.validate("javascript:alert(1)"))
        assertFalse(validator.validate("data:text/html,<script>alert(1)</script>"))
        assertFalse(validator.validate("ftp://example.com/file"))
        assertFalse(validator.validate("file:///etc/passwd"))
    }

    @Test
    fun `absolute URLs with allowlist validation`() {
        val validator = RelayValidator(allowedRelayStateUrls = listOf("https://myapp.example.com/"))

        // Allowed origin
        assertTrue(validator.validate("https://myapp.example.com/dashboard"))

        // Blocked: wrong origin, port, or scheme
        assertFalse(validator.validate("https://evil.com/phish"))
        assertFalse(validator.validate("https://myapp.example.com:8443/dashboard"))
        assertFalse(validator.validate("http://myapp.example.com/dashboard"))

        // Blocked: bypass attempts
        assertFalse(validator.validate("https://myapp.example.com@evil.com/phish"))
        assertFalse(validator.validate("https://myapp.example.com.evil.com/phish"))
    }

    @Test
    fun `path prefix matching with segment boundaries`() {
        // Prefix without trailing slash - exact match only
        val validatorNoSlash = RelayValidator(allowedRelayStateUrls = listOf("https://myapp.example.com/app"))
        assertTrue(validatorNoSlash.validate("https://myapp.example.com/app/dashboard"))
        assertFalse(validatorNoSlash.validate("https://myapp.example.com/application"))

        // Prefix with trailing slash - allows subpaths
        val validatorWithSlash = RelayValidator(allowedRelayStateUrls = listOf("https://myapp.example.com/app/"))
        assertTrue(validatorWithSlash.validate("https://myapp.example.com/app/dashboard"))
        assertFalse(validatorWithSlash.validate("https://myapp.example.com/app"))
    }

    @Test
    fun `relative path prefix matching`() {
        // Prefix with trailing slash
        val validator = RelayValidator(allowedRelayStateUrls = listOf("/app/"))
        assertTrue(validator.validate("/app/dashboard"))
        assertTrue(validator.validate("/app/"))
        assertTrue(validator.validate("/app/../admin")) // Path normalization left to browser
        assertFalse(validator.validate("/admin"))
        assertFalse(validator.validate("/application"))

        // Prefix without trailing slash
        val validatorNoSlash = RelayValidator(allowedRelayStateUrls = listOf("/app"))
        assertTrue(validatorNoSlash.validate("/app"))
        assertFalse(validatorNoSlash.validate("/app/dashboard"))
        assertFalse(validatorNoSlash.validate("/application"))
    }

    @Test
    fun `multiple allowed origins`() {
        val validator = RelayValidator(
            allowedRelayStateUrls = listOf(
                "https://app1.example.com/",
                "https://app2.example.com/"
            )
        )

        assertTrue(validator.validate("https://app1.example.com/page"))
        assertTrue(validator.validate("https://app2.example.com/page"))
        assertFalse(validator.validate("https://app3.example.com/page"))
    }

    @Test
    fun `null allowlist disables validation`() {
        val validator = RelayValidator(allowedRelayStateUrls = null)
        assertTrue(validator.validate("https://any-domain.com/any-path"))
        assertTrue(validator.validate("/any/path"))
    }

    @Test
    fun `host comparison is case insensitive`() {
        val validator = RelayValidator(allowedRelayStateUrls = listOf("https://MyApp.Example.COM/"))
        assertTrue(validator.validate("https://myapp.example.com/page"))
    }

    @Test
    fun `mixed relative and absolute URLs in allowlist`() {
        val validator = RelayValidator(
            allowedRelayStateUrls = listOf(
                "/local/",
                "https://external.com/"
            )
        )

        assertTrue(validator.validate("/local/page"))
        assertFalse(validator.validate("/other/page"))
        assertTrue(validator.validate("https://external.com/page"))
        assertFalse(validator.validate("https://other.com/page"))
    }

    @Test
    fun `default ports are handled correctly`() {
        val validator = RelayValidator(allowedRelayStateUrls = listOf("https://myapp.example.com/"))
        assertTrue(validator.validate("https://myapp.example.com/page"))

        val validatorWithPort = RelayValidator(allowedRelayStateUrls = listOf("https://myapp.example.com:443/"))
        assertTrue(validatorWithPort.validate("https://myapp.example.com/page"))
    }

    @Test
    fun `malformed URLs are rejected`() {
        val validator = RelayValidator(allowedRelayStateUrls = listOf("https://myapp.example.com/"))
        assertFalse(validator.validate("https://[invalid"))
        assertFalse(validator.validate("ht!tp://example.com"))
    }
}
