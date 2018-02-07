package io.ktor.network.tls

enum class TLSHandshakeType(val code: Int) {
    HelloRequest(0x00),
    ClientHello(0x01),
    ServerHello(0x02),
    Certificate(0x0b),
    ServerKeyExchange(0x0c),
    CertificateRequest(0x0d),
    ServerDone(0x0e),
    CertificateVerify(0x0f),
    ClientKeyExchange(0x10),
    Finished(0x14);

    companion object {
        private val byCode = Array(256) { idx -> values().firstOrNull { it.code == idx } }

        fun byCode(code: Int): TLSHandshakeType = when (code) {
            in 0..0xff -> byCode[code]
            else -> null
        } ?: throw IllegalArgumentException("Invalid TLS handshake type code: $code")
    }
}