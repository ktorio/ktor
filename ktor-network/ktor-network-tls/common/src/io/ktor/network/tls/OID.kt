/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.util.*

@InternalAPI
public data class OID(public val identifier: String) {
    public val asArray: IntArray = identifier.split(".", " ").map { it.trim().toInt() }.toIntArray()

    public companion object {
        public val OrganizationName: OID = OID("2.5.4.10")
        public val OrganizationalUnitName: OID = OID("2.5.4.11")
        public val CountryName: OID = OID("2.5.4.6")
        public val CommonName: OID = OID("2.5.4.3")
        public val SubjectAltName: OID = OID("2.5.29.17")

        /**
         * Encryption OID
         */
        public val RSAEncryption: OID = OID("1 2 840 113549 1 1 1")
        public val ECEncryption: OID = OID("1.2.840.10045.2.1")

        /**
         * Algorithm OID
         */
        public val ECDSAwithSHA384Encryption: OID = OID("1.2.840.10045.4.3.3")
        public val ECDSAwithSHA256Encryption: OID = OID("1.2.840.10045.4.3.2")

        public val RSAwithSHA512Encryption: OID = OID("1.2.840.113549.1.1.13")
        public val RSAwithSHA384Encryption: OID = OID("1.2.840.113549.1.1.12")
        public val RSAwithSHA256Encryption: OID = OID("1.2.840.113549.1.1.11")
        public val RSAwithSHA1Encryption: OID = OID("1.2.840.113549.1.1.5")

        /**
         * EC curves
         */
        public val secp256r1: OID = OID("1.2.840.10045.3.1.7")

        public fun fromAlgorithm(algorithm: String): OID = when (algorithm) {
            "SHA1withRSA" -> RSAwithSHA1Encryption
            "SHA384withECDSA" -> ECDSAwithSHA384Encryption
            "SHA256withECDSA" -> ECDSAwithSHA256Encryption
            "SHA384withRSA" -> RSAwithSHA384Encryption
            "SHA256withRSA" -> RSAwithSHA256Encryption
            else -> error("Could't find OID for $algorithm")
        }
    }
}

@InternalAPI
public fun keysGenerationAlgorithm(algorithm: String): String = when {
    algorithm.endsWith("ecdsa", ignoreCase = true) -> "EC"
    algorithm.endsWith("dsa", ignoreCase = true) -> "DSA"
    algorithm.endsWith("rsa", ignoreCase = true) -> "RSA"
    else -> error("Couldn't find KeyPairGenerator algorithm for $algorithm")
}
