package io.ktor.network.tls.extensions

import io.ktor.network.tls.*
import kotlinx.io.core.*

enum class HashAlgorithm(val code: Byte, val openSSLName: String) {
    NONE(0, ""),
    MD5(1, "MD5"),
    SHA1(2, "SHA-1"),
    SHA224(3, "SHA-224"),
    SHA256(4, "SHA-256"),
    SHA384(5, "SHA-384"),
    SHA512(6, "SHA-512");

    companion object {
        fun byCode(code: Byte): HashAlgorithm = values().find { it.code == code }
                ?: throw TLSException("Unknown hash algorithm: $code")
    }
}

enum class SignatureAlgorithm(val code: Byte) {
    ANON(0),
    RSA(1),
    ECDSA(3);

    companion object {
        fun byCode(code: Byte): SignatureAlgorithm = values().find { it.code == code }
                ?: throw TLSException("Unknown signature algorithm: $code")
    }
}

data class HashAndSign(val hash: HashAlgorithm, val sign: SignatureAlgorithm) {
    constructor(hash: Byte, sign: Byte) : this(HashAlgorithm.byCode(hash), SignatureAlgorithm.byCode(sign))

    val name: String = "${hash.name}with${sign.name}"
}

internal val SupportedSignatureAlgorithms: List<HashAndSign> = listOf(
    HashAndSign(HashAlgorithm.SHA384, SignatureAlgorithm.ECDSA),
    HashAndSign(HashAlgorithm.SHA256, SignatureAlgorithm.ECDSA),

    HashAndSign(HashAlgorithm.SHA512, SignatureAlgorithm.RSA),
    HashAndSign(HashAlgorithm.SHA384, SignatureAlgorithm.RSA),
    HashAndSign(HashAlgorithm.SHA256, SignatureAlgorithm.RSA),
    HashAndSign(HashAlgorithm.SHA1, SignatureAlgorithm.RSA)
)

internal fun ByteReadPacket.parseSignatureAlgorithms(): List<HashAndSign> {
    val length = readShort().toInt() and 0xffff

    val result = mutableListOf<HashAndSign>()
    while (remaining > 0) {
        result += readHashAndSign()
    }

    if (remaining != length)
        throw TLSException("Invalid hash and sign packet size: expected $length, actual ${result.size}")

    return result
}

internal fun ByteReadPacket.readHashAndSign(): HashAndSign {
    val hash = readByte()
    val sign = readByte()

    check(sign != SignatureAlgorithm.ANON.code) { "Anonymous signature not al" }

    return HashAndSign(hash, sign)
}
