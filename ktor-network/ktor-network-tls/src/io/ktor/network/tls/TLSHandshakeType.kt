package io.ktor.network.tls

import io.ktor.network.tls.extensions.*

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

enum class ServerKeyExchangeType(val code: Int) {
    ExplicitPrime(1),
    ExplicitChar(2),
    NamedCurve(3);

    companion object {
        private val byCode = Array(256) { idx -> values().firstOrNull() { it.code == idx } }
        fun byCode(code: Int): ServerKeyExchangeType {
            val result = if (code in 0..0xff) byCode[code] else null
            return result ?: throw IllegalArgumentException("Invalid TLS ServerKeyExchange type code: $code")
        }
    }
}

internal class TLSServerHello(
    val version: TLSVersion,
    val serverSeed: ByteArray,
    val sessionId: ByteArray,
    suite: Short,
    val compressionMethod: Short,
    val extensions: List<TLSExtension> = listOf()
) {
    val cipherSuite: CipherSuite = CIOCipherSuites.SupportedSuites.find { it.code == suite }
            ?: error("Server cipher suite is not supported: $suite")

    val hashAndSignAlgorithms: List<HashAndSign>

    init {
        val algorithms = mutableListOf<HashAndSign>()
        extensions.forEach {
            when (it.type) {
                TLSExtensionType.SIGNATURE_ALGORITHMS -> {
                    algorithms += it.packet.parseSignatureAlgorithms()
                }
                else -> {}
            }
        }

        hashAndSignAlgorithms = algorithms
    }
}
