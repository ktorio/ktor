/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

/**
 * Contains the standard auth schemes.
 */
object AuthScheme {
    /**
     * Basic Authentication described in the RFC-7617
     *
     * ```
     * response = base64("$user:$password")
     * ```
     *
     * see https://tools.ietf.org/html/rfc7617)
     */
    const val Basic = "Basic"

    /**
     * Digest Authentication described in the RFC-2069:
     *
     * ```
     * HA1 = MD5("$username:$realm:$password") // What's usually stored
     * HA2 = MD5("$method:$digestURI")
     * response = MD5("$HA1:$nonce:$HA2") // The client and the server sends and checks this.
     * ```
     *
     * see https://tools.ietf.org/html/rfc2069
     */
    const val Digest = "Digest"

    /**
     * Described in the RFC-4599:
     *
     * see https://www.ietf.org/rfc/rfc4559.txt
     */
    const val Negotiate = "Negotiate"

    /**
     * OAuth Authentication described in the RFC-6749:
     *
     * see https://tools.ietf.org/html/rfc6749
     */
    const val OAuth = "OAuth"

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getBasic(): String = Basic

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getDigest(): String = Digest

    @Suppress("KDocMissingDocumentation", "unused")
    @Deprecated("Compatibility", level = DeprecationLevel.HIDDEN)
    fun getNegotiate(): String = Negotiate
}
