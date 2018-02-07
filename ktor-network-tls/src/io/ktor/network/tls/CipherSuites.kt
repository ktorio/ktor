package io.ktor.network.tls


enum class SecretExchangeType {
    RSA,
    DiffieHellman
}

class CipherSuite(val code: Short,
                  val name: String, val openSSLName: String,
                  val exchangeType: SecretExchangeType,
                  val jdkCipherName: String, val keyStrength: Int, val fixedIvLength: Int, val ivLength: Int, val cipherTagSizeInBytes: Int,
                  val macName: String, val macStrength: Int,
                  val hashName: String
) {
    val keyStrengthInBytes = keyStrength / 8
    val macStrengthInBytes = macStrength / 8
}

internal val TLS_RSA_WITH_AES_128_GCM_SHA256 = CipherSuite(0x009c, "TLS_RSA_WITH_AES_128_GCM_SHA256", "AES128_GCM_SHA256", SecretExchangeType.RSA, "AES/GCM/NoPadding", 128, 4, 12, 16, "HmacSHA256", 0, "SHA-256")

internal val CipherSuites = mapOf(0x009c.toShort() to TLS_RSA_WITH_AES_128_GCM_SHA256)
