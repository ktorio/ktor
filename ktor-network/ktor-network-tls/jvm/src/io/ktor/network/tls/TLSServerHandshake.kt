/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.SecretExchangeType.*
import io.ktor.network.tls.extensions.*
import io.ktor.network.tls.extensions.NamedCurve
import io.ktor.utils.io.core.*
import sun.security.util.*
import java.security.*
import java.security.cert.*
import java.security.interfaces.*
import javax.crypto.*
import javax.crypto.spec.*

internal val TLSServerHandshake = TLSHandshakeAlgorithm {
    val clientHello = receiveClientHello()

    initCipherSuite(clientHello, sendServerHello(clientHello))

    // Send server certificate + key info
    val certificate = findCertificate() // TODO support for anonymous negotiation?
        ?: throw TLSNegotiationException("Could not find suitable certificate from keystore")
    sendCertificate(certificate)
    sendServerKeyExchange(certificate)
    sendServerDone()

    receiveClientEncryptionInfo(certificate)
    sendChangeCipherSpec()
    sendServerFinished()
}

private suspend fun TLSSocketAdaptor.receiveClientHello(): TLSClientHello =
    handshakes.receive().let { handshake ->
        check(handshake.type == TLSHandshakeType.ClientHello) {
            ("Expected TLS handshake ClientHello but got ${handshake.type}")
        }
        TLSClientHello.read(handshake.packet)
    }

private suspend fun TLSSocketAdaptor.sendServerHello(
    clientHello: TLSClientHello
): TLSServerHello {
    if (clientHello.version != TLSVersion.TLS12) {
        throw TLSUnsupportedException("TLS version ${clientHello.version} not supported")
    }
    val cipherSuite = clientHello.suites.firstOrNull { it.exchangeType == RSA } // TODO
        ?: throw TLSUnsupportedException("No supported cipher suite requested from client")
    return TLSServerHello(
        header = TLSHelloHeader(
            version = TLSVersion.TLS12,
            seed = generateSeed(),
            sessionId = ByteArray(32),
        ),
        cipherSuite = cipherSuite,
        compressionMethod = 0,
        extensions = clientHello.extensions, // TODO filter by supported
    ).also { hello ->
        output.send(TLSHandshakeType.ServerHello) {
            TLSServerHello.write(this, hello)
        }
    }
}

/**
 * Select a certificate that contains the configured host and has a matching signature algorithm.
 */
private fun TLSSocketAdaptor.findCertificate(): CertificateAndKey? =
    certificates.firstOrNull()
// TODO select from clientHello supported suites
//    config.certificates.find { cert ->
//        config.serverName?.let { serverName ->
//            cert.certificateChain.asSequence().flatMap { it.hosts() }.contains(serverName)
//        } != false && clientHello.suites.any { suite ->
//            suite.signatureAlgorithm.name in cert.certificateChain.map { it.sigAlgName }
//        }
//    }

private suspend fun TLSSocketAdaptor.sendCertificate(certificateAndKey: CertificateAndKey) {
    output.send(TLSHandshakeType.Certificate) {
        writeTLSCertificate.write(this, certificateAndKey.certificateChain.toList())
    }
}

private suspend fun TLSSocketAdaptor.sendServerKeyExchange(certificateAndKey: CertificateAndKey) {
    when (val privateKey = certificateAndKey.key) {
        is RSAPrivateKey -> {} // Key exchange not required for RSA key
        is ECPrivateKey -> {
            val securityProvider = Security.getProvider("SunEC")
            val curveName = ECUtil.getCurveName(securityProvider, privateKey.params)
            val namedCurve = NamedCurve.valueOf(curveName)
            val ecPoint = privateKey.params.generator
            val leaf = certificateAndKey.certificateChain.first()
            val hashAndSign = SupportedSignatureAlgorithms.first { it.name.equals(leaf.sigAlgName, ignoreCase = true) }

            val sign = Signature.getInstance(certificateAndKey.certificateChain.first().sigAlgName)!!
            sign.initSign(certificateAndKey.key)

            digest.state.preview { sign.update(it.readBytes()) }
            val signBytes = sign.sign()!!

            output.send(TLSHandshakeType.ServerKeyExchange) {
                // curve params
                writeCurveParams.write(this, namedCurve) // TODO

                // ec point
                writeECPoint(
                    ecPoint,
                    namedCurve.fieldSize
                )

                // hash + sign
                writeByte(hashAndSign.hash.code)
                writeByte(hashAndSign.sign.code)

                // signed message (for verification)
                writeShort(
                    signBytes
                        .size.toShort()
                )
                writeFully(signBytes)
            }
        }

        else -> throw TLSNegotiationException("Unexpected key type ${privateKey::class.simpleName}")
    }
}

private suspend fun TLSSocketAdaptor.sendServerDone() {
    output.send(TLSHandshakeType.ServerDone)
}

private suspend fun TLSSocketAdaptor.receiveClientEncryptionInfo(serverCertificate: CertificateAndKey) {
    var clientCertificate: X509Certificate? = null

    while (true) {
        val handshake = handshakes.receive()
        val (type, packet) = handshake
        when (type) {
            TLSHandshakeType.Certificate -> {
                // TODO copy pasta
                val certs = readTLSCertificate.read(packet)
                val x509s = certs.filterIsInstance<X509Certificate>()
                if (x509s.isEmpty()) throw TLSValidationException("Server sent no certificate")

                val manager = trustManager
                manager.checkServerTrusted(x509s.toTypedArray(), cipherSuite.exchangeType.jvmName)

                val x509Certificate = x509s.firstOrNull { certificate ->
                    SupportedSignatureAlgorithms.any {
                        val oid = it.oid?.identifier ?: return@any false
                        oid.equals(certificate.sigAlgOID, ignoreCase = true)
                    }
                } ?: throw TLSValidationException("No suitable server certificate received: $certs")

                if (serverName != null) {
                    verifyHostnameInCertificate(serverName, x509Certificate)
                }

                clientCertificate = x509Certificate
            }

            TLSHandshakeType.CertificateVerify -> {
                val hashAndSign = HashAndSign(packet.readByte(), packet.readByte())
                    ?: error("Unknown hash and sign type.")
                val length = packet.readShort()
                val signature: ByteArray = packet.readBytes(length.toInt())
                val verifier: Signature = Signature.getInstance(hashAndSign.name)
                verifier.initVerify(clientCertificate)
                digest.state.preview { verifier.update(it.readBytes()) }

                if (!verifier.verify(signature)) throw TLSValidationException("Failed to verify signed message")
            }

            TLSHandshakeType.ClientKeyExchange -> {
                when (cipherSuite.exchangeType) {
                    ECDHE -> {
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
                            initVerify(clientCertificate)
                            update(
                                buildPacket {
                                    writeFully(clientSeed)
                                    writeFully(serverSeed)
                                    writePacket(params)
                                }.readBytes()
                            )
                        }

                        if (!signature.verify(signedMessage)) throw TLSValidationException(
                            "Failed to verify signed message"
                        )

                        val encryptionInfo = generateECKeys(curve, point)
                        val preSecret = generatePreSecret(encryptionInfo)
                        deriveMasterSecret(preSecret)
                    }

                    RSA -> {
                        val size = packet.readShort().toInt()
                        val encryptedSecret = packet.readBytes(size)
                        val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")!!
                        rsaCipher.init(Cipher.DECRYPT_MODE, serverCertificate.key, random)
                        val preSecret = rsaCipher.doFinal(encryptedSecret)
                        deriveMasterSecret(preSecret)
                    }
                }
            }

            TLSHandshakeType.Finished -> {
                val receivedChecksum = packet.readBytes()
                val finishedSize = Int.SIZE_BYTES + receivedChecksum.size
                val expectedChecksum = PRF(
                    masterSecret,
                    CLIENT_FINISHED_LABEL,
                    digest.doHash(cipherSuite.hash.openSSLName, trimEnd = finishedSize),
                    receivedChecksum.size
                )

                if (!receivedChecksum.contentEquals(expectedChecksum)) {
                    throw TLSValidationException(
                        """Handshake: ClientFinished verification failed:
                        |Expected: ${expectedChecksum.joinToString()}
                        |Actual: ${receivedChecksum.joinToString()}
                        """.trimMargin()
                    )
                }

                return
            }

            else -> throw TLSNegotiationException("Unexpected handshake type: $type")
        }
    }
}

private fun TLSSocketAdaptor.deriveMasterSecret(preSecret: ByteArray) {
    masterSecret = masterSecret(
        SecretKeySpec(preSecret, cipherSuite.hash.macName),
        clientSeed,
        serverSeed
    )
    preSecret.fill(0)
}

// TODO copy pasta
private fun TLSSocketAdaptor.generatePreSecret(encryptionInfo: EncryptionInfo?): ByteArray =
    when (cipherSuite.exchangeType) {
        RSA -> ByteArray(48).also {
            random.nextBytes(it)
            it[0] = 0x03
            it[1] = 0x03
        }

        ECDHE -> KeyAgreement.getInstance("ECDH")!!.run {
            if (encryptionInfo == null) throw TLSValidationException("ECDHE_ECDSA: Encryption info should be provided")
            init(encryptionInfo.myPrivate)
            doPhase(encryptionInfo.theirPublic, true)
            generateSecret()!!
        }
    }

// TODO copy pasta
private suspend fun TLSSocketAdaptor.sendChangeCipherSpec() {
    val packet = buildPacket { writeByte(1) }
    try {
        output.send(TLSRecord(TLSRecordType.ChangeCipherSpec, version, packet))
    } catch (cause: Throwable) {
        packet.release()
        throw cause
    }
}

private suspend fun TLSSocketAdaptor.sendServerFinished() {
    val checksum = digest.doHash(cipherSuite.hash.openSSLName)
    output.send(TLSHandshakeType.Finished) {
        writePacket(
            buildPacket {
                val prf = PRF(masterSecret, SERVER_FINISHED_LABEL, checksum, 12)
                writeFully(prf)
            }
        )
    }
}
