/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import kotlin.test.*

/**
 * Unit tests for RelayStateValidator to prevent open redirect attacks.
 */
class RelayStateValidationTest {

    @Test
    fun `Default validator allows relative paths`() {
        val validator = RelayStateValidator.Default

        // Basic relative paths with various components
        assertTrue(validator.validate("/dashboard"))
        assertTrue(validator.validate("/search?q=test&page=1"))
        assertTrue(validator.validate("/page#section"))
        assertTrue(validator.validate("/any/path"))
        assertTrue(validator.validate("/"))
    }

    @Test
    fun `Default validator allows safe absolute URLs`() {
        val validator = RelayStateValidator.Default

        assertTrue(validator.validate("https://any-domain.com/any-path"))
        assertTrue(validator.validate("http://localhost/test"))
        assertTrue(validator.validate("https://example.com:8080/path"))
    }

    @Test
    fun `Default validator blocks dangerous patterns`() {
        val validator = RelayStateValidator.Default

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
    fun `Default validator blocks URLs with userinfo`() {
        val validator = RelayStateValidator.Default

        assertFalse(validator.validate("https://user@evil.com/phish"))
        assertFalse(validator.validate("https://user:pass@evil.com/phish"))
    }

    @Test
    fun `AllowList validator requires non-empty list`() {
        assertFailsWith<IllegalArgumentException> {
            RelayStateValidator.AllowList(emptyList())
        }
    }

    @Test
    fun `AllowList validator blocks dangerous patterns`() {
        val validator = RelayStateValidator.AllowList("/")

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
    fun `AllowList validator with absolute URL allowlist`() {
        val validator = RelayStateValidator.AllowList("https://myapp.example.com/")

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
    fun `AllowList validator path prefix matching with segment boundaries`() {
        // Prefix without trailing slash - exact match only
        val validatorNoSlash = RelayStateValidator.AllowList("https://myapp.example.com/app")
        assertTrue(validatorNoSlash.validate("https://myapp.example.com/app/dashboard"))
        assertFalse(validatorNoSlash.validate("https://myapp.example.com/application"))

        // Prefix with trailing slash - allows subpaths
        val validatorWithSlash = RelayStateValidator.AllowList("https://myapp.example.com/app/")
        assertTrue(validatorWithSlash.validate("https://myapp.example.com/app/dashboard"))
        assertFalse(validatorWithSlash.validate("https://myapp.example.com/app"))
    }

    @Test
    fun `AllowList validator with multiple allowed origins`() {
        val validator = RelayStateValidator.AllowList(
            "https://app1.example.com/",
            "https://app2.example.com/"
        )

        assertTrue(validator.validate("https://app1.example.com/page"))
        assertTrue(validator.validate("https://app2.example.com/page"))
        assertFalse(validator.validate("https://app3.example.com/page"))
    }

    @Test
    fun `AllowList validator host comparison is case insensitive`() {
        val validator = RelayStateValidator.AllowList("https://MyApp.Example.COM/")
        assertTrue(validator.validate("https://myapp.example.com/page"))
    }

    @Test
    fun `AllowList validator with mixed relative and absolute URLs`() {
        val validator = RelayStateValidator.AllowList(
            "/local/",
            "https://external.com/"
        )

        assertTrue(validator.validate("/local/page"))
        assertFalse(validator.validate("/other/page"))
        assertTrue(validator.validate("https://external.com/page"))
        assertFalse(validator.validate("https://other.com/page"))
    }

    @Test
    fun `AllowList validator handles default ports correctly`() {
        val validator = RelayStateValidator.AllowList("https://myapp.example.com/")
        assertTrue(validator.validate("https://myapp.example.com/page"))

        val validatorWithPort = RelayStateValidator.AllowList("https://myapp.example.com:443/")
        assertTrue(validatorWithPort.validate("https://myapp.example.com/page"))
    }

    @Test
    fun `AllowList validator rejects malformed URLs`() {
        val validator = RelayStateValidator.AllowList("https://myapp.example.com/")
        assertFalse(validator.validate("https://[invalid"))
        assertFalse(validator.validate("ht!tp://example.com"))
    }

    @Test
    fun `Custom validator uses provided function`() {
        val validator = RelayStateValidator.Custom { url ->
            url.startsWith("/safe/") && !url.contains("..")
        }

        assertTrue(validator.validate("/safe/page"))
        assertTrue(validator.validate("/safe/nested/page"))
        assertFalse(validator.validate("/unsafe/page"))
        assertFalse(validator.validate("/safe/../unsafe"))
    }

    @Test
    fun `Custom validator can allow all URLs`() {
        val validator = RelayStateValidator.Custom { true }

        assertTrue(validator.validate("https://any.com/path"))
        assertTrue(validator.validate("/any/path"))
        // Note: Custom validator bypasses safety checks - user is responsible
        assertTrue(validator.validate("javascript:alert(1)"))
    }

    @Test
    fun `Custom validator can block all URLs`() {
        val validator = RelayStateValidator.Custom { false }

        assertFalse(validator.validate("https://safe.com/path"))
        assertFalse(validator.validate("/safe/path"))
    }
}
