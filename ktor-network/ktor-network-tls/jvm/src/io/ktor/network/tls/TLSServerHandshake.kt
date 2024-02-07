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
import java.security.interfaces.ECPrivateKey
import java.security.spec.*
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec

internal object TLSServerHandshake : TLSHandshakeAlgorithm {

    override suspend fun negotiate(connector: TLSHandshakeConnector) {
        with(connector) {
            val clientHello = receiveClientHello()
            val serverHello = sendServerHello(clientHello)

            connect(serverHello.cipherSuite, serverHello.serverSeed, clientHello.clientSeed) {
                // Send server certificate + key info
                val certificate = findCertificate(clientHello) // TODO support for anonymous negotiation?
                    ?: throw TLSException("Could not find suitable certificate from keystore")
                sendCertificate(certificate)
                sendServerKeyExchange(certificate)
                sendServerDone()

                // Receive client certificate + key info
                val clientCertificate = receiveClientCertificate()
                val encryptionInfo = receiveClientKeyExchange(clientCertificate)
                val preSecret = generatePreSecret(encryptionInfo)

                masterSecret = masterSecret(
                    SecretKeySpec(preSecret, cipherSuite.hash.macName),
                    clientSeed,
                    serverSeed
                )
                preSecret.fill(0)

                receiveClientCertificateVerify(clientCertificate)
                receiveClientFinished()
                sendServerFinished()
            }
        }
    }

    private suspend fun TLSHandshakeConnector.receiveClientHello(): TLSClientHello =
        readRawRecord().let { record ->
            val handshake = TLSHandshake.read(record.packet)
            check(handshake.type == TLSHandshakeType.ServerHello) {
                ("Expected TLS handshake ServerHello but got ${handshake.type}")
            }
            TLSClientHello.read(handshake.packet)
        }


    private suspend fun TLSHandshakeConnector.sendServerHello(
        clientHello: TLSClientHello
    ): TLSServerHello {
        if (clientHello.version != TLSVersion.TLS12)
            throw TLSException("TLS version ${clientHello.version} not supported")
        val cipherSuite = clientHello.suites.firstOrNull { it.isSupported() }
            ?: throw TLSException("No supported cipher suite requested from client")
        return TLSServerHello(
            version = TLSVersion.TLS12,
            serverSeed = generateSeed(),
            sessionId = ByteArray(32),
            cipherSuite = cipherSuite,
            compressionMethod = 0,
            extensions = clientHello.extensions // TODO filter by supported
        ).also { hello ->
            sendRawRecord(TLSHandshakeType.ServerHello) {
               TLSServerHello.write(this, hello)
            }
        }
    }

    /**
     * Select a certificate that contains the configured host and has a matching signature algorithm.
     */
    private fun TLSHandshakeCtx.findCertificate(clientHello: TLSClientHello): CertificateAndKey? =
        config.certificates.find { cert ->
            config.serverName?.let { serverName ->
                cert.certificateChain.asSequence().flatMap { it.hosts() }.contains(serverName)
            } != false && clientHello.suites.any { suite ->
                suite.signatureAlgorithm.name in cert.certificateChain.map { it.sigAlgName }
            }
        }

    private suspend fun TLSHandshakeCtx.sendCertificate(certificateAndKey: CertificateAndKey) {
        sendRecord(TLSHandshakeType.Certificate) {
            writeTLSCertificate.write(this, certificateAndKey.certificateChain.toList())
        }
    }

    private suspend fun TLSHandshakeCtx.sendServerKeyExchange(certificateAndKey: CertificateAndKey) {
        val privateKey = certificateAndKey.key
        if (privateKey !is ECPrivateKey)
            throw TLSException("Expected certificate to be EC private key but was ${privateKey::class.simpleName}")
        // TODO no bueno
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

        sendServerKeyExchange(
            namedCurve,
            ecPoint,
            hashAndSign,
            signBytes
        )
    }

    private suspend fun TLSHandshakeCtx.sendServerKeyExchange(
        namedCurve: NamedCurve,
        ecPoint: ECPoint,
        hashAndSign: HashAndSign,
        signedMessage: ByteArray,
    ) {
        sendRecord(TLSHandshakeType.ServerKeyExchange) {
            // curve params
            writeCurveParams.write(this, namedCurve) // TODO

            // ec point
            writeECPoint(ecPoint, namedCurve.fieldSize)

            // hash + sign
            writeByte(hashAndSign.hash.code)
            writeByte(hashAndSign.sign.code)

            // signed message (for verification)
            writeShort(signedMessage.size.toShort())
            writeFully(signedMessage)
        }
    }

    private suspend fun TLSHandshakeCtx.sendServerDone() {
        sendRecord(TLSHandshakeType.ServerDone)
    }

    private suspend fun TLSHandshakeCtx.receiveClientCertificate(): X509Certificate {
        val handshake = handshakes.receive()
        when(handshake.type) {
            TLSHandshakeType.Certificate -> {
                // TODO copy pasta
                val certs = readTLSCertificate.read(handshake.packet)
                val x509s = certs.filterIsInstance<X509Certificate>()
                if (x509s.isEmpty()) throw TLSException("Server sent no certificate")

                val manager = config.trustManager
                manager.checkServerTrusted(x509s.toTypedArray(), cipherSuite.exchangeType.jvmName)

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
            else -> throw TLSException("Expected certificate but was ${handshake.type}")
        }
    }

    private suspend fun TLSHandshakeCtx.receiveClientKeyExchange(clientCertificate: X509Certificate): EncryptionInfo {
        val handshake = handshakes.receive()
        val packet = handshake.packet
        when(handshake.type) {
            TLSHandshakeType.ClientKeyExchange -> {
                when(cipherSuite.exchangeType) {
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

                        if (!signature.verify(signedMessage)) throw TLSException("Failed to verify signed message")

                        return generateECKeys(curve, point)
                    }
                    RSA -> {
                        error("Client key exchange handshake doesn't expected in RCA exchange type")
                    }
                }
            }
            else -> throw TLSException("Expected client key exchange but was ${handshake.type.name}")
        }
    }

    // TODO copy pasta
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

    // TODO optional
    private suspend fun TLSHandshakeCtx.receiveClientCertificateVerify(certificate: X509Certificate) {
        val handshake = handshakes.receive()
        when (handshake.type) {
            TLSHandshakeType.CertificateVerify -> {
                val packet = handshake.packet
                val hashAndSign = HashAndSign(packet.readByte(), packet.readByte())
                    ?: error("Unknown hash and sign type.")
                val length = packet.readShort()
                val signature: ByteArray = packet.readBytes(length.toInt())
                val verifier: Signature = Signature.getInstance(hashAndSign.name)
                verifier.initVerify(certificate)
                digest.state.preview { verifier.update(it.readBytes()) }

                if (!verifier.verify(signature)) throw TLSException("Failed to verify signed message")
            }

            else -> throw TLSException("Expected certificate verify but was ${handshake.type}")
        }
    }

    private suspend fun TLSHandshakeCtx.receiveClientFinished() {
        val handshake = handshakes.receive()
        if (handshake.type != TLSHandshakeType.Finished)
            throw TLSException("Expected finished but was ${handshake.type}")

        val receivedChecksum = handshake.packet.readBytes()
        val expectedChecksum = PRF(
            masterSecret,
            CLIENT_FINISHED_LABEL,
            digest.doHash(cipherSuite.hash.openSSLName),
            receivedChecksum.size
        )

        if (!receivedChecksum.contentEquals(expectedChecksum)) {
            throw TLSException(
                """Handshake: ClientFinished verification failed:
                |Expected: ${expectedChecksum.joinToString()}
                |Actual: ${receivedChecksum.joinToString()}
                """.trimMargin()
            )
        }
    }

    private suspend fun TLSHandshakeCtx.sendServerFinished() {
        val checksum = digest.doHash(cipherSuite.hash.openSSLName)
        val finished = finished(checksum, masterSecret)
        sendRecord(TLSHandshakeType.Finished) {
            writePacket(finished)
        }
    }

}
