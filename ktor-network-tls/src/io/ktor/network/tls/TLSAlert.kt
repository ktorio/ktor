package io.ktor.network.tls

enum class TLSAlertLevel(val code: Int) {
    WARNING(1),
    FATAL(2);


    companion object {
        private val byCode = Array(256) { idx -> TLSAlertLevel.values().firstOrNull { it.code == idx } }

        fun byCode(code: Int): TLSAlertLevel = when (code) {
            in 0..255 -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS record type code: $code")
    }
}

enum class TLSAlertType(val code: Int) {
    CloseNotify(0),
    UnexpectedMessage(10),
    BadRecordMac(20),
    DecryptionFailed_RESERVED(21),
    RecordOverflow(22),
    DecompressionFailure(30),
    HandshakeFailure(40),
    NoCertificate_RESERVED(41),
    BadCertificate(42),
    UnsupportedCertificate(43),
    CertificateRevoked(44),
    CertificateExpired(45),
    CertificateUnknown(46),
    IllegalParameter(47),
    UnknownCa(48),
    AccessDenied(49),
    DecodeError(50),
    DecryptError(51),

    ExportRestriction_RESERVED(60),
    ProtocolVersion(70),
    InsufficientSecurity(71),
    InternalError(80),
    UserCanceled(90),
    NoRenegotiation(100),
    UnsupportedExtension(110);

    companion object {
        private val byCode = Array(256) { idx -> TLSAlertType.values().firstOrNull { it.code == idx } }

        fun byCode(code: Int): TLSAlertType = when (code) {
            in 0..255 -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS record type code: $code")
    }
}
