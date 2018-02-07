package io.ktor.network.tls

import kotlinx.io.core.*
import kotlinx.io.core.ByteReadPacket
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*
import kotlin.coroutines.experimental.*

fun BytePacketBuilder.writeTLSHeader(header: TLSRecordHeader) {
    writeByte(header.type.code.toByte())
    writeShort(header.version.code.toShort())
    writeShort(header.length.toShort())
}

fun BytePacketBuilder.writeTLSHandshake(handshake: TLSHandshakeHeader) {
    if (handshake.length > 0xffffff) throw TLSException("TLS handshake size limit exceeded: ${handshake.length}")
    val v = (handshake.type.code shl 24) or handshake.length
    writeInt(v)
}

fun BytePacketBuilder.writeTLSClientHello(hello: TLSHandshakeHeader) {
    writeShort(hello.version.code.toShort())
    writeFully(hello.random)

    if (hello.sessionIdLength < 0 || hello.sessionIdLength > 0xff || hello.sessionIdLength > hello.sessionId.size) throw TLSException("Illegal sessionIdLength")
    writeByte(hello.sessionIdLength.toByte())
    writeFully(hello.sessionId, 0, hello.sessionIdLength)

    writeShort((hello.suitesCount * 2).toShort())
    val suites = hello.suites
    for (i in 0 until hello.suitesCount) {
        writeShort(suites[i])
    }

    // compression is always null
    writeByte(1)
    writeByte(0)

    val extensions = ArrayList<ByteReadPacket>()
    extensions += buildSignatureAlgorithmsExtension()
    hello.serverName?.let { name ->
        extensions += buildServerNameExtension(name)
    }

    writeShort(extensions.sumBy { it.remaining }.toShort())
    for (e in extensions) {
        writePacket(e)
    }
}

private fun buildSignatureAlgorithmsExtension(): ByteReadPacket {
    return buildPacket {
        writeShort(0x000d) // signature_algorithms
        val signaturesCount = 3
        writeShort((2 + signaturesCount * 2).toShort()) // length in bytes
        writeShort((signaturesCount * 2).toShort()) // length in bytes

        writeShort(0x0601) // sha512+RSA
        writeShort(0x0501) // sha384+RSA
        writeShort(0x0401) // sha256+RSA
    }
}

private fun buildServerNameExtension(name: String): ByteReadPacket {
    return buildPacket {
        writeShort(0) // server_name
        writeShort((name.length + 2 + 1 + 2).toShort()) // lengthh
        writeShort((name.length + 2 + 1).toShort()) // list length
        writeByte(0) // type: host_name
        writeShort(name.length.toShort()) // name length
        writeStringUtf8(name)
    }
}

fun BytePacketBuilder.writeEncryptedPreMasterSecret(preSecret: ByteArray, publicKey: PublicKey, random: SecureRandom) {
    require(preSecret.size == 48)

    val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")!!
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, random)
    val encryptedSecret = rsaCipher.doFinal(preSecret)

    if (encryptedSecret.size > 0xffff) throw TLSException("Encrypted premaster secret is too long")

    writeShort(encryptedSecret.size.toShort())
    writeFully(encryptedSecret)
}

fun BytePacketBuilder.writeChangeCipherSpec(header: TLSRecordHeader) {
    header.type = TLSRecordType.ChangeCipherSpec
    header.length = 1

    writeTLSHeader(header)
    writeByte(1)
}

internal suspend fun finished(
        messages: List<ByteReadPacket>,
        baseHash: String,
        secretKey: SecretKeySpec,
        coroutineContext: CoroutineContext
): ByteReadPacket {
    val digestBytes = hashMessages(messages, baseHash, coroutineContext)
    return finished(digestBytes, secretKey)
}

internal fun finished(digest: ByteArray, secretKey: SecretKey) = buildPacket {
    val prf = PRF(secretKey, CLIENT_FINISHED_LABEL, digest, 12)
    writeFully(prf)
}

internal fun serverFinished(handshakeHash: ByteArray, secretKey: SecretKey, length: Int = 12): ByteArray =
        PRF(secretKey, SERVER_FINISHED_LABEL, handshakeHash, length)
