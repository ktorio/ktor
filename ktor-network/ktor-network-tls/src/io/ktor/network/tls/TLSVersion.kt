package io.ktor.network.tls

enum class TLSVersion(val code: Int) {
    SSL3(0x0300),
    TLS10(0x0301),
    TLS11(0x0302),
    TLS12(0x0303);

    companion object {
        private val byOrdinal = values()

        fun byCode(code: Int): TLSVersion = when (code) {
            in 0x0300 .. 0x0303 -> byOrdinal[code - 0x0300]
            else -> throw IllegalArgumentException("Invalid TLS version code $code")
        }
    }
}