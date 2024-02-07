/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.network.tls.SecretExchangeType.*
import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.core.*
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import javax.crypto.*
import javax.crypto.spec.*
import javax.security.auth.x500.*

internal object TLSClientHandshake : TLSHandshakeAlgorithm {

    override suspend fun negotiate(connector: TLSHandshakeConnector) {
        with(connector) {
            val clientHello = sendClientHello()
            val serverHello = receiveServerHello()

            connect(serverHello.cipherSuite, serverHello.serverSeed, clientHello.clientSeed) {
                handleCertificatesAndKeys(cipherSuite.exchangeType)
                receiveServerFinished()
            }
        }
    }

    private suspend fun TLSHandshakeConnector.sendClientHello(): TLSClientHello {
        return TLSClientHello(
            version = TLSVersion.TLS12,
            clientSeed = generateSeed(),
            sessionId = ByteArray(32),
            suites = cipherSuites,
            compressionMethod = 0x0010, // no compression
            extensions = listOfNotNull(
                serverName?.let(::buildServerNameExtension)
            ),
        ).also { hello ->
            sendRawRecord(TLSHandshakeType.ClientHello) {
                TLSClientHello.write(this, hello)
            }
        }
    }

    private suspend fun TLSHandshakeConnector.receiveServerHello(): TLSServerHello =
        readRawRecord().let { record ->
            val handshake = TLSHandshake.read(record.packet)
            check(handshake.type == TLSHandshakeType.ServerHello) {
                ("Expected TLS handshake ServerHello but got ${handshake.type}")
            }
            TLSServerHello.read(handshake.packet).also {
                verifyHello(it)
            }
        }

    private fun TLSHandshakeConnector.verifyHello(serverHello: TLSServerHello) {
        val suite = serverHello.cipherSuite
        check(suite in config.cipherSuites) { "Unsupported cipher suite ${suite.name} in SERVER_HELLO" }

        val clientExchanges = SupportedSignatureAlgorithms.filter {
            it.hash == suite.hash && it.sign == suite.signatureAlgorithm
        }

        if (clientExchanges.isEmpty()) {
            throw TLSException("No appropriate hash algorithm for suite: $suite")
        }

        val serverExchanges = serverHello.hashAndSignAlgorithms
        if (serverExchanges.isEmpty()) return

        if (!clientExchanges.any { it in serverExchanges }) {
            val message = "No sign algorithms in common. \n" +
                "Server candidates: $serverExchanges \n" +
                "Client candidates: $clientExchanges"

            throw TLSException(message)
        }
    }

    private suspend fun TLSHandshakeCtx.handleCertificatesAndKeys(exchangeType: SecretExchangeType) {
        var serverCertificate: X509Certificate? = null
        var certificateInfo: CertificateInfo? = null
        var encryptionInfo: EncryptionInfo? = null

        while (true) {
            val handshake = handshakes.receive()
            val packet = handshake.packet

            when (handshake.type) {
                TLSHandshakeType.Certificate -> {
                    serverCertificate = readServerCertificate(packet, exchangeType)
                }
                TLSHandshakeType.CertificateRequest -> {
                    certificateInfo = readClientCertificateRequest(packet)
                }
                TLSHandshakeType.ServerKeyExchange -> {
                    when (exchangeType) {
                        ECDHE -> {
                            encryptionInfo = readECKeysFromPacket(packet, serverCertificate)
                        }
                        RSA -> {
                            packet.release()
                            error("Server key exchange handshake doesn't expected in RCA exchange type")
                        }
                    }
                }
                TLSHandshakeType.ServerDone -> {
                    handleServerDone(
                        exchangeType,
                        serverCertificate!!,
                        certificateInfo,
                        encryptionInfo
                    )
                    return
                }

                else -> throw TLSException("Unsupported message type during handshake: ${handshake.type}")
            }
        }
    }

    private suspend fun TLSHandshakeCtx.readServerCertificate(
        packet: ByteReadPacket,
        exchangeType: SecretExchangeType
    ): X509Certificate {
        val certs = readTLSCertificate.read(packet)
        val x509s = certs.filterIsInstance<X509Certificate>()
        if (x509s.isEmpty()) throw TLSException("Server sent no certificate")

        val manager = config.trustManager
        manager.checkServerTrusted(x509s.toTypedArray(), exchangeType.jvmName)

        val x509Certificate = x509s.firstOrNull { certificate ->
            SupportedSignatureAlgorithms.any {
                val oid = it.oid?.identifier ?: return@any false
                oid.equals(certificate.sigAlgOID, ignoreCase = true)
            }
        } ?: throw TLSException("No suitable server certificate received: $certs")

        if (config.serverName != null) {
            verifyHostnameInCertificate(config.serverName, x509Certificate)
        }

        return x509Certificate
    }

    private suspend fun TLSHandshakeCtx.readECKeysFromPacket(
        packet: ByteReadPacket,
        serverCertificate: X509Certificate?
    ): EncryptionInfo {
        val curve = readCurveParams.read(packet)
        val point = readECPoint(curve.fieldSize, packet)
        val hashAndSign = packet.readHashAndSign()
            ?: error("Unknown hash and sign type.")
        val signSize = packet.readShort().toInt() and 0xffff
        val signedMessage = packet.readBytes(signSize)

        val params = buildPacket {
            // TODO: support other curve types
            writeByte(ServerKeyExchangeType.NamedCurve.code.toByte())
            writeShort(curve.code)
            writeECPoint(point, curve.fieldSize)
        }

        val signature = Signature.getInstance(hashAndSign.name)!!.apply {
            initVerify(serverCertificate)
            update(
                buildPacket {
                    writeFully(clientSeed)
                    writeFully(serverSeed)
                    writePacket(params)
                }.readBytes()
            )
        }

        if (!signature.verify(signedMessage)) throw TLSException("Failed to verify signed message")

        return generateECKeys(curve, point)
    }

    private suspend fun TLSHandshakeCtx.handleServerDone(
        exchangeType: SecretExchangeType,
        serverCertificate: Certificate,
        certificateInfo: CertificateInfo?,
        encryptionInfo: EncryptionInfo?
    ) {
        val chain = certificateInfo?.let { sendClientCertificate(it) }

        val preSecret: ByteArray = generatePreSecret(encryptionInfo)

        sendClientKeyExchange(
            exchangeType,
            serverCertificate,
            preSecret,
            encryptionInfo
        )

        masterSecret = masterSecret(
            SecretKeySpec(preSecret, cipherSuite.hash.macName),
            clientSeed,
            serverSeed
        )
        preSecret.fill(0)

        chain?.let { sendClientCertificateVerify(certificateInfo, it) }

        sendChangeCipherSpec()
        sendClientFinished(masterSecret)
    }

    private fun TLSHandshakeCtx.generatePreSecret(encryptionInfo: EncryptionInfo?): ByteArray =
        when (cipherSuite.exchangeType) {
            RSA -> ByteArray(48).also {
                config.random.nextBytes(it)
                it[0] = 0x03
                it[1] = 0x03
            }

            ECDHE -> KeyAgreement.getInstance("ECDH")!!.run {
                if (encryptionInfo == null) throw TLSException("ECDHE_ECDSA: Encryption info should be provided")
                init(encryptionInfo.myPrivate)
                doPhase(encryptionInfo.theirPublic, true)
                generateSecret()!!
            }
        }

    private suspend fun TLSHandshakeCtx.sendClientKeyExchange(
        exchangeType: SecretExchangeType,
        serverCertificate: Certificate,
        preSecret: ByteArray,
        encryptionInfo: EncryptionInfo?
    ) {
        val packet = when (exchangeType) {
            RSA -> buildPacket {
                writeEncryptedPreMasterSecret(preSecret, serverCertificate.publicKey, config.random)
            }

            ECDHE -> buildPacket {
                if (encryptionInfo == null) throw TLSException("ECDHE: Encryption info should be provided")
                writePublicKeyUncompressed(encryptionInfo.myPublic)
            }
        }

        sendRecord(TLSHandshakeType.ClientKeyExchange) { writePacket(packet) }
    }

    private suspend fun TLSHandshakeCtx.sendClientCertificate(info: CertificateInfo): CertificateAndKey? {
        val chainAndKey = config.certificates.find { candidate ->
            val leaf = candidate.certificateChain.first()

            val validAlgorithm = when (leaf.publicKey.algorithm) {
                "RSA" -> info.types.contains(CertificateType.RSA)
                "DSS" -> info.types.contains(CertificateType.DSS)
                else -> false
            }

            if (!validAlgorithm) return@find false

            val hasHashAndSignInCommon = info.hashAndSign.none {
                it.name.equals(leaf.sigAlgName, ignoreCase = true)
            }

            if (hasHashAndSignInCommon) return@find false

            info.authorities.isEmpty() || candidate.certificateChain
                .map { X500Principal(it.issuerX500Principal.name) }
                .any { it in info.authorities }
        }

        sendRecord(TLSHandshakeType.Certificate) {
            writeTLSCertificates(chainAndKey?.certificateChain ?: emptyArray())
        }

        return chainAndKey
    }

    private suspend fun TLSHandshakeCtx.sendClientCertificateVerify(info: CertificateInfo, certificateAndKey: CertificateAndKey) {
        val leaf = certificateAndKey.certificateChain.first()
        val hashAndSign = info.hashAndSign.firstOrNull {
            it.name.equals(leaf.sigAlgName, ignoreCase = true)
        } ?: return

        if (hashAndSign.sign == SignatureAlgorithm.DSA) return

        val sign = Signature.getInstance(certificateAndKey.certificateChain.first().sigAlgName)!!
        sign.initSign(certificateAndKey.key)

        sendRecord(TLSHandshakeType.CertificateVerify) {
            writeByte(hashAndSign.hash.code)
            writeByte(hashAndSign.sign.code)

            digest.state.preview { sign.update(it.readBytes()) }
            val signBytes = sign.sign()!!

            writeShort(signBytes.size.toShort())
            writeFully(signBytes)
        }
    }

    private suspend fun TLSHandshakeCtx.sendChangeCipherSpec() {
        val packet = buildPacket { writeByte(1) }
        try {
            output.send(TLSRecord(TLSRecordType.ChangeCipherSpec, packet = packet))
        } catch (cause: Throwable) {
            packet.release()
            throw cause
        }
    }

    private suspend fun TLSHandshakeCtx.sendClientFinished(masterKey: SecretKeySpec) {
        val checksum = digest.doHash(cipherSuite.hash.openSSLName)
        val finished = finished(checksum, masterKey)
        sendRecord(TLSHandshakeType.Finished) {
            writePacket(finished)
        }
    }

    private suspend fun TLSHandshakeCtx.receiveServerFinished() {
        val finished = handshakes.receive()

        if (finished.type != TLSHandshakeType.Finished) {
            throw TLSException("Finished handshake expected, received: $finished")
        }

        val receivedChecksum = finished.packet.readBytes()
        val expectedChecksum = PRF(
            masterSecret,
            SERVER_FINISHED_LABEL,
            digest.doHash(cipherSuite.hash.openSSLName),
            receivedChecksum.size
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

}

/**
 * RFC 5246, 7.4.4.  Certificate Request:
 *
 *     struct {
 *         ClientCertificateType certificate_types<1..2^8-1>;
 *         SignatureAndHashAlgorithm supported_signature_algorithms<2^16-1>;
 *         DistinguishedName certificate_authorities<0..2^16-1>;
 *     } CertificateRequest;
 */
internal fun readClientCertificateRequest(packet: ByteReadPacket): CertificateInfo {
    val typeCount = packet.readByte().toInt() and 0xFF
    val types = packet.readBytes(typeCount)

    val hashAndSignCount = packet.readShort().toInt() and 0xFFFF
    val hashAndSign = mutableListOf<HashAndSign>()

    repeat(hashAndSignCount / 2) {
        val hash = packet.readByte()
        val sign = packet.readByte()
        hashAndSign += HashAndSign.byCode(hash, sign) ?: return@repeat
    }

    val authoritiesSize = packet.readShort().toInt() and 0xFFFF
    val authorities = mutableSetOf<X500Principal>()

    var position = 0
    while (position < authoritiesSize) {
        val size = packet.readShort().toInt() and 0xFFFF
        val bytesForReadingSize = Short.SIZE_BYTES
        position += size + bytesForReadingSize

        val authority = packet.readBytes(size)
        authorities += X500Principal(authority)
    }

    val certificateInfo = CertificateInfo(types, hashAndSign.toTypedArray(), authorities)
    check(packet.isEmpty)
    return certificateInfo
}
