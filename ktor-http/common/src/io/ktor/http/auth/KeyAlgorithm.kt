/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

import kotlin.jvm.JvmInline

/**
 * Represents a cryptographic key algorithm family.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.auth.KeyAlgorithm)
 *
 * @property name The common name of the key algorithm family.
 */
@JvmInline
public value class KeyAlgorithm(public val name: String) {
    public companion object {
        /**
         * RSA key algorithm family.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.auth.KeyAlgorithm.Companion.RSA)
         */
        public val RSA: KeyAlgorithm = KeyAlgorithm("RSA")

        /**
         * Elliptic Curve key algorithm family.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.auth.KeyAlgorithm.Companion.EC)
         */
        public val EC: KeyAlgorithm = KeyAlgorithm("EC")

        /**
         * HMAC symmetric key algorithm family.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.auth.KeyAlgorithm.Companion.HMAC)
         */
        public val HMAC: KeyAlgorithm = KeyAlgorithm("HMAC")

        /**
         * Octet Key Pair key algorithm family.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.auth.KeyAlgorithm.Companion.OKP)
         */
        public val OKP: KeyAlgorithm = KeyAlgorithm("OKP")
    }
}
