package io.ktor.network.tls

internal data class OID(val identifier: String) {
    val asArray: IntArray = identifier.split(".", " ").map { it.trim().toInt() }.toIntArray()

    companion object {
        val OrganizationName = OID("2.5.4.10")
        val OrganizationalUnitName = OID("2.5.4.11")
        val CountryName = OID("2.5.4.6")
        val CommonName = OID("2.5.4.3")
        val SubjectAltName = OID("2.5.29.17")

        /**
         * Encryption OID
         */
        val RSAEncryption = OID("1 2 840 113549 1 1 1")
        val ECEncryption = OID("1.2.840.10045.2.1")

        /**
         * Algorithm OID
         */
        val Sha1withRSAEncryption = OID("1.2.840.113549.1.1.5")
        val ECDSAwithSHA384Encryption = OID("1.2.840.10045.4.3.3")
        val ECDSAwithSHA256Encryption = OID("1.2.840.10045.4.3.2")

        val RSAwithSHA384Encryption = OID("1.2.840.113549.1.1.12")
        val RSAwithSHA256Encryption = OID("1.2.840.113549.1.1.11")

        /**
         * EC curves
         */
        val secp256r1 = OID("1.2.840.10045.3.1.7")

        fun fromAlgorithm(algorithm: String): OID = when (algorithm) {
            "SHA1withRSA" -> Sha1withRSAEncryption
            "SHA384withECDSA" -> ECDSAwithSHA384Encryption
            "SHA256withECDSA" -> ECDSAwithSHA256Encryption
            "SHA384withRSA" -> RSAwithSHA384Encryption
            "SHA256withRSA" -> RSAwithSHA256Encryption
            else -> error("Could't find OID for $algorithm")
        }
    }
}

internal fun keysGenerationAlgorithm(algorithm: String): String = when {
    algorithm.endsWith("ecdsa", ignoreCase = true) -> "EC"
    algorithm.endsWith("dsa", ignoreCase = true) -> "DSA"
    algorithm.endsWith("rsa", ignoreCase = true) -> "RSA"
    else -> error("Couldn't find KeyPairGenerator algorithm for $algorithm")
}
