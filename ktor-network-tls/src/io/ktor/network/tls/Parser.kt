package io.ktor.network.tls

import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.packet.*
import kotlinx.coroutines.experimental.io.packet.ByteReadPacket
import kotlinx.io.core.*
import sun.security.x509.*
import java.io.*
import java.security.cert.*
import kotlin.experimental.*

suspend fun ByteReadChannel.readTLSRecordHeader(header: TLSRecordHeader): Boolean {
    val typeCode = try { readByte().toInt() and 0xff } catch (t: ClosedReceiveChannelException) { return false }
    header.type = TLSRecordType.byCode(typeCode)
    header.version = readTLSVersion()
    header.length = readShort().toInt() and 0xffff

    if (header.length > MAX_TLS_FRAME_SIZE) throw TLSException("Illegal TLS frame size: ${header.length}")
    return true
}

suspend fun ByteReadChannel.readTLSHandshake(header: TLSRecordHeader, handshake: TLSHandshakeHeader) {
    if (header.type !== TLSRecordType.Handshake) throw TLSException("Expected TLS handshake but got ${header.type}")

    val v = readInt()
    handshake.type = TLSHandshakeType.byCode(v ushr 24)
    handshake.length = v and 0xffffff
}

fun ByteReadPacket.readTLSHandshake(handshake: TLSHandshakeHeader): ByteReadPacket {
    val v = readInt()
    handshake.type = TLSHandshakeType.byCode(v ushr 24)
    handshake.length = v and 0xffffff

    val body = readBytes(handshake.length)
    return buildPacket {
        writeFully(body)
    }
}

suspend fun ByteReadChannel.readTLSClientHello(header: TLSRecordHeader, handshake: TLSHandshakeHeader) {
    readTLSHandshake(header, handshake)
    val p = readPacket(handshake.length)

    if (handshake.type !== TLSHandshakeType.ClientHello) throw TLSException("Expected TLS handshake ClientHello but got ${handshake.type}")

    handshake.version = p.readTLSVersion()
    p.readFully(handshake.random)
    val sessionIdLength = p.readByte().toInt() and 0xff

    if (sessionIdLength > 32) throw TLSException("sessionId length limit of 32 bytes exceeded: $sessionIdLength specified")
    handshake.sessionIdLength = sessionIdLength
    p.readFully(handshake.sessionId, 0, sessionIdLength)

    val cipherSuitesSize = p.readShort().toInt() and 0xffff
    val suitesCount = cipherSuitesSize / 2

    val suites = if (suitesCount > 255) ShortArray(cipherSuitesSize / 2).also { handshake.suites = it } else handshake.suites
    handshake.suitesCount = suitesCount

    for (i in 0 until suites.size) {
        suites[i] = p.readShort()
    }

    p.discardExact(2) // skip compression

    if (p.remaining > 0) {
        val extensionsLength = p.readShort().toInt() and 0xffff
        p.discardExact(extensionsLength)

        // TODO TLS extensions
    }

    if (p.remaining > 0) {
        throw TLSException("TLS handshake extra bytes found")
    }
}

fun ByteReadPacket.readTLSServerHello(handshake: TLSHandshakeHeader) {
    if (handshake.type !== TLSHandshakeType.ServerHello) throw TLSException("Expected TLS handshake ServerHello but got ${handshake.type}")

    handshake.version = readTLSVersion()
    readFully(handshake.random)
    val sessionIdLength = readByte().toInt() and 0xff

    if (sessionIdLength > 32) throw TLSException("sessionId length limit of 32 bytes exceeded: $sessionIdLength specified")
    handshake.sessionIdLength = sessionIdLength
    readFully(handshake.sessionId, 0, sessionIdLength)

    handshake.suitesCount = 1
    handshake.suites[0] = readShort()

    val compressionMethod = readByte().toShort() and 0xff
    if (compressionMethod.toInt() != 0) throw TLSException("Unsupported TLS compression method $compressionMethod (only null 0 compression method is supported)")

    if (remaining > 0) {
        val extensionsLength = readShort().toInt() and 0xffff
        discardExact(extensionsLength)

        // TODO TLS extensions
    }

    if (remaining > 0) {
        throw TLSException("TLS handshake ServerHello extra bytes")
    }
}

fun ByteReadPacket.readTLSCertificate(handshake: TLSHandshakeHeader): List<Certificate> {
    if (handshake.type !== TLSHandshakeType.Certificate) throw TLSException("Expected TLS handshake Certificate but got ${handshake.type}")

    val certificatesChainLength = readTripleByteLength()
    var certificateBase = 0
    val result = ArrayList<Certificate>()

    while (certificateBase < certificatesChainLength) {
        val certificateLength = readTripleByteLength()
        if (certificateLength > (certificatesChainLength - certificateBase)) throw TLSException("Certificate length is too big")
        if (certificateLength > remaining) throw TLSException("Certificate length is too big")

        val certificate = ByteArray(certificateLength)
        readFully(certificate)
        certificateBase += certificateLength + 3

        result.add(X509CertImpl(certificate))
    }

    return result
}

private const val MAX_TLS_FRAME_SIZE = 0x4800

class TLSException(message: String, cause: Throwable? = null) : IOException(message, cause)

private suspend fun ByteReadChannel.readTLSVersion() =
        TLSVersion.byCode(readShort().toInt() and 0xffff)

private fun ByteReadPacket.readTLSVersion() =
        TLSVersion.byCode(readShort().toInt() and 0xffff)

private fun ByteReadPacket.readTripleByteLength(): Int = (readByte().toInt() and 0xff shl 16) or
        (readShort().toInt() and 0xffff)
