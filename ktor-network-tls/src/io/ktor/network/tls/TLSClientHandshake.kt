package io.ktor.network.tls

import io.ktor.network.tls.SecretExchangeType.*
import io.ktor.network.tls.extensions.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.security.interfaces.*
import java.security.spec.*
import javax.crypto.*
import javax.crypto.spec.*
import javax.net.ssl.*
import kotlin.coroutines.experimental.*

private data class EncryptionInfo(
    val serverPublic: PublicKey,
    val clientPublic: PublicKey,
    val clientPrivate: PrivateKey
)

internal class TLSClientHandshake(
    rawInput: ByteReadChannel,
    rawOutput: ByteWriteChannel,
    coroutineContext: CoroutineContext,
    private val trustManager: X509TrustManager? = null,
    randomAlgorithm: String = "NativePRNGNonBlocking",
    private val serverName: String? = null
) {
    private val digest = Digest()
    private val random = SecureRandom.getInstance(randomAlgorithm)!!
    private val clientSeed: ByteArray = random.generateClientSeed()

    @Volatile
    private lateinit var serverHello: TLSServerHello

    @Volatile
    private lateinit var masterSecret: SecretKeySpec

    private val key: ByteArray by lazy {
        with(serverHello.cipherSuite) {
            keyMaterial(
                masterSecret, serverHello.serverSeed + clientSeed,
                keyStrengthInBytes, macStrengthInBytes, fixedIvLength
            )
        }
    }

    val input: ReceiveChannel<TLSRecord> = produce(coroutineContext) {
        var packetCounter = 0L
        var useCipher = false

        loop@ while (true) {
            val record = rawInput.readTLSRecord()
            val rawPacket = record.packet

            val packet = if (useCipher) {
                val packetSize = rawPacket.remaining
                val recordIv = rawPacket.readLong()
                val cipher = decryptCipher(
                    serverHello.cipherSuite,
                    key, record.type, packetSize, recordIv, packetCounter++
                )

                rawPacket.decrypted(cipher)
            } else rawPacket

            when (record.type) {
                TLSRecordType.Alert -> {
                    val level = TLSAlertLevel.byCode(packet.readByte().toInt())
                    val code = TLSAlertType.byCode(packet.readByte().toInt())
                    val cause = TLSException("Received alert during handshake. Level: $level, code: $code")

                    channel.close(cause)
                    return@produce
                }
                TLSRecordType.ChangeCipherSpec -> {
                    check(!useCipher)
                    val flag = packet.readByte()
                    if (flag != 1.toByte()) throw TLSException("Expected flag: 1, received $flag in ChangeCipherSpec")
                    useCipher = true
                    continue@loop
                }
                else -> {
                }
            }

            channel.send(TLSRecord(record.type, packet = packet))
        }
    }

    val output: SendChannel<TLSRecord> = actor(coroutineContext) {
        var packetCounter = 0L
        var useCipher = false

        channel.consumeEach { rawRecord ->
            val record = if (useCipher) {
                val cipher = encryptCipher(
                    serverHello.cipherSuite,
                    key, rawRecord.type, rawRecord.packet.remaining, packetCounter, packetCounter
                )

                val packet = rawRecord.packet.encrypted(cipher, packetCounter)
                packetCounter++

                TLSRecord(rawRecord.type, packet = packet)
            } else rawRecord

            if (rawRecord.type == TLSRecordType.ChangeCipherSpec) {
                useCipher = true
            }

            rawOutput.writeRecord(record)
        }
    }

    private val handshakes = produce<TLSHandshake>(coroutineContext) {
        while (true) {
            val record = input.receive()
            val packet = record.packet

            while (packet.remaining > 0) {
                val handshake = packet.readTLSHandshake()
                if (handshake.type == TLSHandshakeType.HelloRequest) continue
                if (handshake.type != TLSHandshakeType.Finished) {
                    digest += handshake
                }

                channel.send(handshake)

                if (handshake.type == TLSHandshakeType.Finished) return@produce
            }
        }
    }

    suspend fun negotiate() {
        sendClientHello()
        serverHello = receiveServerHello()

        val signatureAlgorithm = selectAndVerifyAlgorithm(serverHello)
        handleCertificatesAndKeys(signatureAlgorithm)
        receiveServerFinished()
    }

    private fun selectAndVerifyAlgorithm(serverHello: TLSServerHello): HashAndSign {
        val suite = serverHello.cipherSuite
        check(suite in SupportedSuites)

        val clientExchanges = SupportedSignatureAlgorithms.filter {
            it.hash == suite.hash && it.sign == suite.signatureAlgorithm
        }

        if (clientExchanges.isEmpty())
            throw TLSException("No appropriate hash algorithm for suite: $suite")

        val serverExchanges = serverHello.hashAndSignAlgorithms
        if (serverExchanges.isEmpty()) return clientExchanges.first()

        return clientExchanges.firstOrNull { it in serverExchanges } ?: throw TLSException(
            "No sign algorithms in common. \n" +
                    "Server candidates: $serverExchanges \n" +
                    "Client candidates: $clientExchanges"
        )
    }

    private suspend fun sendClientHello() {
        sendHandshakeRecord(TLSHandshakeType.ClientHello) {
            // TODO: support session id
            writeTLSClientHello(TLSVersion.TLS12, SupportedSuites, clientSeed, ByteArray(32), serverName)
        }
    }

    private suspend fun receiveServerHello(): TLSServerHello {
        val handshake = handshakes.receive()

        check(handshake.type == TLSHandshakeType.ServerHello) {
            ("Expected TLS handshake ServerHello but got ${handshake.type}")
        }

        return handshake.packet.readTLSServerHello()
    }

    private suspend fun handleCertificatesAndKeys(signatureAlgorithm: HashAndSign) {
        val exchangeType = serverHello.cipherSuite.exchangeType
        var serverCertificate: Certificate? = null
        var certificateRequested = false
        var preferableSignatureAlgorithm = signatureAlgorithm
        var encryptionInfo: EncryptionInfo? = null

        while (true) {
            val handshake = handshakes.receive()
            val packet = handshake.packet

            when (handshake.type) {
                TLSHandshakeType.Certificate -> {
                    val certs = packet.readTLSCertificate()
                    val x509s = certs.filterIsInstance<X509Certificate>()

                    val authType = when (exchangeType) {
                        RSA -> "RSA"
                        ECDHE -> "EC"
                    }

                    val manager: X509TrustManager = trustManager ?: findTrustManager()
                    manager.checkServerTrusted(x509s.toTypedArray(), authType)

                    serverCertificate = x509s.firstOrNull { certificate ->
                        SupportedSignatureAlgorithms.any { it.name.equals(certificate.sigAlgName, ignoreCase = true) }
                    } ?: throw TLSException("No suitable server certificate received: $certs")
                }
                TLSHandshakeType.CertificateRequest -> {
                    certificateRequested = true
                    check(packet.remaining == 0)
                }
                TLSHandshakeType.ServerKeyExchange -> {
                    when (exchangeType) {
                        ECDHE -> {
                            val curve = packet.readCurveParams()
                            val point = packet.readECPoint(curve.fieldSize)
                            val hashAndSign = packet.readHashAndSign()

                            if (
                                SupportedSignatureAlgorithms.indexOf(hashAndSign) >
                                SupportedSignatureAlgorithms.indexOf(signatureAlgorithm)
                            ) throw TLSException(
                                "Selected algorithms doesn't match with server previously negotiated:" +
                                        " expected $signatureAlgorithm," +
                                        " actual $hashAndSign"
                            )

                            preferableSignatureAlgorithm = hashAndSign

                            val params = buildPacket {
                                // TODO: support other curve types
                                writeByte(ServerKeyExchangeType.NamedCurve.code.toByte())
                                writeShort(curve.code)
                                writeECPoint(point, curve.fieldSize)
                            }

                            val signature = Signature.getInstance(preferableSignatureAlgorithm.name)!!.apply {
                                initVerify(serverCertificate)
                                update(buildPacket {
                                    writeFully(clientSeed)
                                    writeFully(serverHello.serverSeed)
                                    writePacket(params)
                                }.readBytes())
                            }

                            val signSize = packet.readShort().toInt() and 0xffff
                            val signedMessage = packet.readBytes(signSize)
                            if (!signature.verify(signedMessage)) throw TLSException("Failed to verify signed message")

                            encryptionInfo = generateECKeys(curve, point)
                        }
                        SecretExchangeType.RSA ->
                            error("Server key exchange handshake doesn't expected in RCA exchange type")
                    }
                }
                TLSHandshakeType.ServerDone -> {
                    handleServerDone(
                        preferableSignatureAlgorithm,
                        serverCertificate!!,
                        certificateRequested,
                        encryptionInfo
                    )
                    return
                }
                else -> throw TLSException("Unsupported message type during handshake: ${handshake.type}")
            }
        }
    }

    private suspend fun handleServerDone(
        signatureAlgorithm: HashAndSign,
        serverCertificate: Certificate,
        certificateRequested: Boolean,
        encryptionInfo: EncryptionInfo?
    ) {
        if (certificateRequested) sendClientCertificate()

        val preSecret = generatePreSecret(encryptionInfo)
        sendClientKeyExchange(signatureAlgorithm, serverCertificate, preSecret, certificateRequested, encryptionInfo)
        masterSecret = masterSecret(
            SecretKeySpec(preSecret, serverHello.cipherSuite.macName),
            clientSeed, serverHello.serverSeed
        )
        preSecret.fill(0)

        if (certificateRequested) sendClientCertificateVerify()

        sendChangeCipherSpec()
        sendClientFinished(masterSecret)
    }

    private fun generatePreSecret(encryptionInfo: EncryptionInfo?): ByteArray = when (serverHello.cipherSuite.exchangeType) {
        SecretExchangeType.RSA -> random.generateSeed(48)!!.also {
            it[0] = 0x03
            it[1] = 0x03
        }
        SecretExchangeType.ECDHE -> KeyAgreement.getInstance("ECDH")!!.run {
            if (encryptionInfo == null) throw TLSException("ECDHE_ECDSA: Encryption info should be provided")
            init(encryptionInfo.clientPrivate)
            doPhase(encryptionInfo.serverPublic, true)
            generateSecret()!!
        }
    }

    private suspend fun sendClientKeyExchange(
        signatureAlgorithm: HashAndSign,
        serverCertificate: Certificate,
        preSecret: ByteArray,
        certificateRequested: Boolean,
        encryptionInfo: EncryptionInfo?
    ) {
        val packet = when (signatureAlgorithm.sign) {
            SignatureAlgorithm.RSA -> buildPacket {
                writeEncryptedPreMasterSecret(preSecret, serverCertificate.publicKey, random)
            }
            SignatureAlgorithm.ECDSA -> buildPacket {
                if (certificateRequested) return@buildPacket // Key exchange has already completed implicit in the certificate message.
                if (encryptionInfo == null) throw TLSException("ECDHE_ECDSA: Encryption info should be provided")

                writePublicKeyUncompressed(encryptionInfo.clientPublic)
            }
            SignatureAlgorithm.ANON -> throw TLSException("Anon signature couldn't be used to exchange keys")
        }

        sendHandshakeRecord(TLSHandshakeType.ClientKeyExchange, { writePacket(packet) })
    }

    private fun sendClientCertificate() {
        throw TLSException("Client certificates unsupported")
    }

    private fun sendClientCertificateVerify() {
        throw TLSException("Client certificates unsupported")
    }

    private suspend fun sendChangeCipherSpec() {
        output.send(TLSRecord(TLSRecordType.ChangeCipherSpec, packet = buildPacket { writeByte(1) }))
    }

    private suspend fun sendClientFinished(masterKey: SecretKeySpec) {
        val checksum = digest.doHash(serverHello.cipherSuite.hash.openSSLName)
        val finished = finished(checksum, masterKey)
        sendHandshakeRecord(TLSHandshakeType.Finished) {
            writePacket(finished)
        }
    }

    private suspend fun receiveServerFinished() {
        val finished = handshakes.receive()

        if (finished.type != TLSHandshakeType.Finished)
            throw TLSException("Finished handshake expected, received: $finished")

        val receivedChecksum = finished.packet.readBytes()
        val expectedChecksum = serverFinished(
            digest.doHash(serverHello.cipherSuite.hash.openSSLName), masterSecret, receivedChecksum.size
        )

        if (!receivedChecksum.contentEquals(expectedChecksum)) {
            throw TLSException(
                """Handshake: ServerFinished verification failed:
                |Expected: ${expectedChecksum.joinToString()}
                |Actual: ${receivedChecksum.joinToString()}
            """.trimMargin()
            )
        }
    }

    private suspend fun sendHandshakeRecord(handshakeType: TLSHandshakeType, block: BytePacketBuilder.() -> Unit) {
        val handshakeBody = buildPacket(block = block)

        val recordBody = buildPacket {
            writeTLSHandshakeType(handshakeType, handshakeBody.remaining)
            writePacket(handshakeBody)
        }

        digest.update(recordBody)
        output.send(TLSRecord(TLSRecordType.Handshake, packet = recordBody))
    }
}

private fun findTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    val manager = factory.trustManagers

    return manager.first { it is X509TrustManager } as X509TrustManager
}

private fun SecureRandom.generateClientSeed(): ByteArray {
    return generateSeed(32)!!.also {
        val unixTime = (System.currentTimeMillis() / 1000L)
        it[0] = (unixTime shr 24).toByte()
        it[1] = (unixTime shr 16).toByte()
        it[2] = (unixTime shr 8).toByte()
        it[3] = (unixTime shr 0).toByte()
    }
}

private fun generateECKeys(curve: NamedCurve, serverPoint: ECPoint): EncryptionInfo {
    val clientKeys = KeyPairGenerator.getInstance("EC")!!.run {
        initialize(ECGenParameterSpec(curve.name))
        generateKeyPair()!!
    }

    @Suppress("UNCHECKED_CAST")
    val publicKey = clientKeys.public as ECPublicKey
    val factory = KeyFactory.getInstance("EC")!!
    val serverPublic = factory.generatePublic(ECPublicKeySpec(serverPoint, publicKey.params!!))!!

    return EncryptionInfo(serverPublic, clientKeys.public, clientKeys.private)
}
