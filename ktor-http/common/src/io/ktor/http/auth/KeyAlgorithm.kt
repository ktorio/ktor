/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.auth

import kotlin.jvm.JvmInline

/**
 * Represents a cryptographic key algorithm family.
 *
 * @property name The common name of the key algorithm family.
 */
@JvmInline
public value class KeyAlgorithm(public val name: String) {
    public companion object {
        /** RSA key algorithm family. */
        public val RSA: KeyAlgorithm = KeyAlgorithm("RSA")

        /** Elliptic Curve key algorithm family. */
        public val EC: KeyAlgorithm = KeyAlgorithm("EC")

        /** HMAC symmetric key algorithm family. */
        public val HMAC: KeyAlgorithm = KeyAlgorithm("HMAC")

        /** Octet Key Pair key algorithm family. */
        public val OKP: KeyAlgorithm = KeyAlgorithm("OKP")
    }
}
