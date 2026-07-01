/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.http.*
import java.net.URI

/**
 * Validates RelayState URLs to prevent open redirect attacks.
 *
 * SAML RelayState is a parameter passed through the authentication flow that typically
 * contains a URL to redirect users to after authentication.
 *
 * Three implementations are provided:
 * - [RelayStateValidator.Default]: Allows all URLs that pass basic safety checks (default)
 * - [RelayStateValidator.AllowList]: Only allows URLs matching a configured allowlist
 * - [RelayStateValidator.Custom]: Uses a user-provided validation function
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.saml.RelayStateValidator)
 */
public interface RelayStateValidator {
    /**
     * Validates that the relay state URL is safe for redirect.
     *
     * @param url The RelayState URL to validate
     * @return `true` if the URL is allowed, `false` otherwise
     */
    public fun validate(url: String): Boolean

    /**
     * Default validator that allows all URLs passing basic safety checks.
     *
     * This validator permits:
     * - Relative paths starting with `/`
     * - Absolute HTTP/HTTPS URLs
     *
     * It rejects:
     * - Protocol-relative URLs (`//example.com`)
     * - URLs with backslashes
     * - URLs with control characters
     * - Non-HTTP(S) schemes (javascript:, data:, ftp:, etc.)
     * - URLs with userinfo (`https://user:pass@host`)
     */
    public data object Default : RelayStateValidator {
        override fun validate(url: String): Boolean = when {
            containsDangerousPatterns(url) -> false
            url.startsWith("/") -> normalizeRelativePath(url) != null
            else -> isAbsoluteUrlSafe(url)
        }
    }

    /**
     * Validator that only allows URLs matching a configured allowlist.
     *
     * URLs must pass basic safety checks AND match one of the allowed prefixes.
     *
     * @param allowedUrls List of allowed URL prefixes (must not be empty). Can include:
     *   - Relative paths starting with `/` (e.g., `/app/`)
     *   - Absolute URLs (e.g., `https://myapp.example.com/`)
     *
     * For absolute URLs, matching checks:
     * - Exact protocol match (case-insensitive)
     * - Exact host match (case-insensitive per RFC 3986)
     * - Exact port match
     * - Path prefix match with segment boundary validation
     *
     * @throws IllegalArgumentException if [allowedUrls] is empty
     */
    public class AllowList(private val allowedUrls: List<String>) : RelayStateValidator {

        init {
            require(allowedUrls.isNotEmpty()) { "AllowList requires at least one allowed URL" }
        }

        public constructor(vararg allowedUrls: String) : this(allowedUrls.toList())

        override fun validate(url: String): Boolean {
            if (containsDangerousPatterns(url)) {
                return false
            }
            if (url.startsWith("/")) {
                val normalized = normalizeRelativePath(url) ?: return false
                val pathPrefixes = allowedUrls.filter { it.startsWith("/") && !it.startsWith("//") }
                return pathPrefixes.any { prefix ->
                    normalized.startsWith(prefix) && (normalized == prefix || prefix.endsWith("/"))
                }
            }
            if (!isAbsoluteUrlSafe(url)) {
                return false
            }
            return allowedUrls.any { prefix -> isAllowedAbsoluteRelayState(url, prefix) }
        }

        /**
         * Validates an absolute URL against an allowed prefix.
         * Checks a scheme, host, port, and path with segment boundary validation.
         */
        private fun isAllowedAbsoluteRelayState(targetUrl: String, allowedPrefix: String): Boolean {
            val target = runCatching { Url(targetUrl) }.getOrNull() ?: return false
            val allowed = runCatching { Url(allowedPrefix) }.getOrNull() ?: return false

            // Reject URLs with userinfo (user:pass@host) - bypass technique
            if (target.user != null || target.password != null) return false

            // Only allow http and https schemes
            if (target.protocol.name !in listOf("http", "https")) return false
            if (allowed.protocol.name !in listOf("http", "https")) return false

            // Exact protocol match (case-insensitive)
            if (!target.protocol.name.equals(allowed.protocol.name, ignoreCase = true)) return false

            // Exact host match (case-insensitive per RFC 3986)
            if (!target.host.equals(allowed.host, ignoreCase = true)) return false

            // Exact port match
            if (target.port != allowed.port) return false

            // Path prefix match with segment boundary check
            val allowedPath = allowed.encodedPath.ifBlank { "/" }
            val targetPath = target.encodedPath.ifBlank { "/" }

            if (!targetPath.startsWith(allowedPath)) return false
            if (targetPath == allowedPath) return true
            if (allowedPath.endsWith("/")) return true
            return targetPath.getOrNull(allowedPath.length) == '/'
        }
    }

    /**
     * Validator that uses a custom validation function.
     *
     * **Note:** The custom function is responsible for all validation,
     * including basic safety checks. Consider using [Default] or [AllowList]
     * for common use cases, or use the helper functions in [RelayStateValidator.Companion]
     * to implement safety checks.
     *
     * @param validator Function that returns `true` if the URL is allowed
     */
    public class Custom(private val validator: (String) -> Boolean) : RelayStateValidator {
        override fun validate(url: String): Boolean = validator(url)
    }

    public companion object {
        /**
         * Dangerous patterns include:
         * - Protocol-relative URLs (starting with `//`)
         * - Backslashes (potential path traversal on Windows)
         * - Control characters
         */
        public fun containsDangerousPatterns(url: String): Boolean {
            return url.startsWith("//") || url.contains("\\") || url.any { it.isISOControl() }
        }

        /**
         * Validates and normalizes a relative path.
         *
         * A valid relative path:
         * - Starts with `/`
         * - After normalization, still starts with `/` and doesn't become protocol-relative
         *
         * @param url The URL to normalize
         * @return The normalized path if valid, or `null` if invalid
         */
        public fun normalizeRelativePath(url: String): String? {
            if (!url.startsWith("/")) return null
            val normalized = URI(url).normalize().toString()
            return if (normalized.startsWith("/") && !normalized.startsWith("//")) normalized else null
        }

        private val PROTOCOLS = listOf("http", "https")

        /**
         * Checks if an absolute URL is safe for redirect.
         *
         * A safe absolute URL:
         * - Uses HTTP or HTTPS scheme
         * - Has no userinfo (user:pass@host)
         */
        public fun isAbsoluteUrlSafe(url: String): Boolean {
            val parsed = runCatching { Url(url) }.getOrNull() ?: return false
            // Reject URLs with userinfo
            if (parsed.user != null || parsed.password != null) return false
            // Only allow http and https schemes
            return parsed.protocol.name in PROTOCOLS
        }
    }
}
