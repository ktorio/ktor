package io.ktor.network.tls

/**
 * TLS record type with it's numeric [code]
 * @property code numeric record type code
 */
@Suppress("KDocMissingDocumentation")
enum class TLSRecordType(val code: Int) {
    ChangeCipherSpec(0x14),
    Alert(0x15),
    Handshake(0x16),
    ApplicationData(0x17);

    companion object {
        private val byCode = Array(256) { idx -> values().firstOrNull { it.code == idx } }

        /**
         * Find an instance of [TLSRecordType] by it's numeric code or fail
         */
        fun byCode(code: Int): TLSRecordType = when (code) {
            in 0..255 -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS record type code: $code")
    }
}
