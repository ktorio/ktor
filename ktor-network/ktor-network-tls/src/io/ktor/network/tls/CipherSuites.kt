package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.network.tls.platform.*


enum class SecretExchangeType {
    ECDHE,
    RSA
}

data class CipherSuite(
    val code: Short,
    val name: String,
    val openSSLName: String,
    val exchangeType: SecretExchangeType,
    val jdkCipherName: String,
    val keyStrength: Int,
    val fixedIvLength: Int,
    val ivLength: Int, // SecurityParameters.record_iv_length + SecurityParameters.fixed_iv_length rfc5246
    val cipherTagSizeInBytes: Int,
    val macName: String,
    val macStrength: Int,
    val hash: HashAlgorithm,
    val signatureAlgorithm: SignatureAlgorithm
) {
    val keyStrengthInBytes = keyStrength / 8
    val macStrengthInBytes = macStrength / 8
}


/**
 * CIO cipher suites collection
 * https://www.ietf.org/rfc/rfc5289.txt
 * https://tools.ietf.org/html/rfc5288#section-3
 */
object CIOCipherSuites {
    val TLS_RSA_WITH_AES_128_GCM_SHA256 = CipherSuite(
        0x009c, "TLS_RSA_WITH_AES_128_GCM_SHA256", "AES128-GCM-SHA256",
        SecretExchangeType.RSA, "AES/GCM/NoPadding",
        128, 4, 12, 16,
        "HmacSHA256", 0,
        HashAlgorithm.SHA256, SignatureAlgorithm.RSA
    )

    val ECDHE_ECDSA_AES256_SHA384 = CipherSuite(
        0xc02c.toShort(), "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "ECDHE-ECDSA-AES256-GCM-SHA384",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        256, 4, 12, 16, "HmacSHA384", 0,
        HashAlgorithm.SHA384, SignatureAlgorithm.ECDSA
    )

    val ECDHE_ECDSA_AES128_SHA256 = CipherSuite(
        0xc02b.toShort(), "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "ECDHE-ECDSA-AES128-GCM-SHA256",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        128, 4, 12, 16, "HmacSHA256", 0,
        HashAlgorithm.SHA256, SignatureAlgorithm.ECDSA
    )

    val ECDHE_RSA_AES256_SHA384 = CipherSuite(
        0xc030.toShort(), "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "ECDHE-RSA-AES256-GCM-SHA384",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        256, 4, 12, 16, "HmacSHA384", 0,
        HashAlgorithm.SHA384, SignatureAlgorithm.RSA
    )

    val ECDHE_RSA_AES128_SHA256 = CipherSuite(
        0xc02f.toShort(), "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "ECDHE-RSA-AES128-GCM-SHA256",
        SecretExchangeType.ECDHE, "AES/GCM/NoPadding",
        128, 4, 12, 16, "HmacSHA256", 0,
        HashAlgorithm.SHA256, SignatureAlgorithm.RSA
    )

    /**
     * List of suites supported by current platform
     */
    val SupportedSuites: List<CipherSuite> = listOf(
        ECDHE_ECDSA_AES256_SHA384,
        ECDHE_RSA_AES256_SHA384,
        ECDHE_ECDSA_AES128_SHA256,
        ECDHE_RSA_AES128_SHA256,
        TLS_RSA_WITH_AES_128_GCM_SHA256
    ).filter { it.isSupported() }
}

internal fun CipherSuite.isSupported(): Boolean {
    when (platformVersion.major) {
        "1.8.0" -> if (platformVersion.minor < 161 && keyStrength > 128) return false
    }

    return true
}
