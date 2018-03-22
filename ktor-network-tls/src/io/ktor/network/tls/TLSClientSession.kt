package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.packet.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteReadPacket
import java.security.*
import java.security.cert.*
import javax.crypto.*
import javax.crypto.spec.*
import javax.net.ssl.*
import kotlin.coroutines.experimental.*

internal class TLSClientSession(
        val input: ByteReadChannel,
        val output: ByteWriteChannel,
        val trustManager: X509TrustManager? = null,
        val serverName: String? = null,
        val coroutineContext: CoroutineContext,
        randomAlgorithm: String = "NativePRNGNonBlocking"
) : AReadable, AWritable {
    private var readerJob: ReaderJob? = null
    private var writerJob: WriterJob? = null

    private val recordHeader = TLSRecordHeader()

    private val handshakeHeader = TLSHandshakeHeader()
    private val handshakesPacket = WritePacket()

    private var cipherSuite: CipherSuite? = null
    private var serverRandom: ByteArray = EmptyByteArray
    private var serverKey: PublicKey? = null
    private var clientRandom: ByteArray = EmptyByteArray

    private var preSecret = EmptyByteArray
    private var masterSecret: SecretKey? = null

    private var keyMaterial: ByteArray = EmptyByteArray

    private val random = SecureRandom.getInstance(randomAlgorithm)

    suspend fun negotiate() {
        try {
            tlsHandshakeAndNegotiation()
        } catch (t: Throwable) {
            readerJob?.cancel(t)
            writerJob?.cancel(t)
            output.close(t)
            throw t
        }
    }

    private suspend fun tlsHandshakeAndNegotiation() {
        initClientRandom()
        sendClientHello()

        while (true) {
            if (!readTLSRecordHeader()) throw TLSException("Handshake failed: premature end of stream")
            val record = readRecord()

            while (!record.isEmpty) {
                if (processHandshakeMessage(record)) return
            }
        }
    }

    private suspend fun processHandshakeMessage(record: ByteReadPacket): Boolean {
        when (recordHeader.type) {
            TLSRecordType.Handshake -> {
                val body = record.readTLSHandshake(handshakeHeader)

                if (handshakeHeader.type != TLSHandshakeType.HelloRequest) {
                    handshakesPacket.writeTLSHandshake(handshakeHeader)
                    if (!body.isEmpty) handshakesPacket.writePacket(body.copy())
                }

                handshake(body)
            }
            TLSRecordType.ChangeCipherSpec -> {
                if (recordHeader.length != 1) throw TLSException("ChangeCipherSpec should contain just one byte but there are ${recordHeader.length}")
                val flag = record.readByte()
                changeCipherSpec(flag)

                // A Finished message is always sent immediately after a change
                // cipher spec message to verify that the key exchange and
                // authentication processes were successful.
                receiveHandshakeFinished()
                return true
            }
            TLSRecordType.Alert -> {
                val level = TLSAlertLevel.byCode(record.readByte().toInt())
                val code = TLSAlertType.byCode(record.readByte().toInt())

                throw TLSException("Received alert during handshake. Level: $level, code: $code")
            }
            else -> throw TLSException("Unsupported TLS record type ${recordHeader.type}")
        }

        return false
    }

    override fun attachForReading(channel: ByteChannel): WriterJob {
        writerJob = writer(coroutineContext, channel) {
            appDataInputLoop(this.channel)
        }
        return writerJob!!
    }

    override fun attachForWriting(channel: ByteChannel): ReaderJob {
        readerJob = reader(coroutineContext, channel) {
            appDataOutputLoop(this.channel)
        }
        return readerJob!!
    }

    private suspend fun appDataInputLoop(pipe: ByteWriteChannel) {
        var seq = 1L
        while (true) {
            if (!readTLSRecordHeader()) break
            val encrypted = readRecord()

            when (recordHeader.type) {
                TLSRecordType.ApplicationData -> {
                    val recordIv = encrypted.readLong()
                    val cipher = decryptCipher(cipherSuite!!, keyMaterial, recordHeader.type, recordHeader.length, recordIv, seq)
                    val packet = encrypted.decrypted(cipher)

                    pipe.writePacket(packet)
                    pipe.flush()
                }
                TLSRecordType.Alert -> {
                    val recordIv = encrypted.readLong()
                    val cipher = decryptCipher(cipherSuite!!, keyMaterial, recordHeader.type, recordHeader.length, recordIv, seq)
                    val packet = encrypted.decrypted(cipher)

                    val fatal = packet.readByte() == 2.toByte()
                    val code = packet.readByte()

                    if (fatal) {
                        pipe.close(TLSException("Fatal: server alerted with description code $code"))
                    } else {
                        if (code != 0.toByte()) {
                            println("Got TLS warning $code")
                        }
                        pipe.close()
                    }
                    return
                }
                else -> throw TLSException("Unexpected record ${recordHeader.type} (${recordHeader.length} bytes)")
            }

            seq++
        }
    }

    private suspend fun appDataOutputLoop(pipe: ByteReadChannel) {
        var seq = 1L
        val buffer = DefaultByteBufferPool.borrow()

        try {
            while (true) {
                buffer.clear()
                val rc = pipe.readAvailable(buffer)
                if (rc == -1) break

                buffer.flip()
                val cipher = encryptCipher(cipherSuite!!, keyMaterial, TLSRecordType.ApplicationData, rc, seq, seq)
                val packet = buildPacket {
                    writeFully(buffer)
                }
                val encrypted = packet.encrypted(cipher, seq)
                output.writePacket {
                    recordHeader.type = TLSRecordType.ApplicationData
                    recordHeader.version = TLSVersion.TLS12
                    recordHeader.length = encrypted.remaining
                    writeTLSHeader(recordHeader)
                }
                output.writePacket(encrypted)
                output.flush()

                seq++
            }
        } finally {
            DefaultByteBufferPool.recycle(buffer)
        }
    }

    private fun initClientRandom() {
        clientRandom = random.generateSeed(32).apply {
            val unixTime = (System.currentTimeMillis() / 1000L)
            this[0] = (unixTime shr 24).toByte()
            this[1] = (unixTime shr 16).toByte()
            this[2] = (unixTime shr 8).toByte()
            this[3] = (unixTime shr 0).toByte()
        }
    }

    private suspend fun readTLSRecordHeader(): Boolean {
        return input.readTLSRecordHeader(recordHeader)
    }

    private suspend fun readRecord(): ByteReadPacket {
        return input.readPacket(recordHeader.length)
    }

    private suspend fun changeCipherSpec(flag: Byte) {
        if (!readTLSRecordHeader()) throw TLSException("Handshake failed: premature end of stream")
        if (recordHeader.type == TLSRecordType.Handshake) {
            check(flag == 1.toByte()) { "Flag expected to equals 1 in handshake" }
            return
        }

        // TODO: verify flag
        throw TLSException("Unexpected record of type ${recordHeader.type} (${recordHeader.length} bytes)")
    }

    private suspend fun receiveHandshakeFinished() {
        val encryptedPacket = readRecord()
        val recordIv = encryptedPacket.readLong()
        val cipher = decryptCipher(cipherSuite!!, keyMaterial, TLSRecordType.Handshake, recordHeader.length, recordIv, 0)
        val decrypted = encryptedPacket.decrypted(cipher)

        val body = decrypted.readTLSHandshake(handshakeHeader).readBytes()

        if (handshakeHeader.type != TLSHandshakeType.Finished)
            throw TLSException("TLS handshake failed: expected Finihsed record after ChangeCipherSpec but got ${handshakeHeader.type}")

        check(decrypted.isEmpty)

        val expectedFinished = serverFinished(doHash(), masterSecret!!, body.size)
        check(expectedFinished.contentEquals(body)) {
            """Handshake: ServerFinished verification failed:
                |Expected: ${expectedFinished.joinToString()}
                |Actual: ${body.joinToString()}
            """.trimMargin()
        }
    }

    private suspend fun handshake(packet: ByteReadPacket) {
        when (handshakeHeader.type) {
            TLSHandshakeType.ServerHello -> {
                packet.readTLSServerHello(handshakeHeader)
                serverRandom = handshakeHeader.random.copyOf()
                cipherSuite = CipherSuites[handshakeHeader.suites[0]]
            }
            TLSHandshakeType.Certificate -> {
                val certs = packet.readTLSCertificate(handshakeHeader)
                val x509s = certs.filterIsInstance<X509Certificate>()

                val tm: X509TrustManager = trustManager ?: findTrustManager()

                certs.forEach {
                    tm.checkServerTrusted(x509s.toTypedArray(), "RSA")
                }

                serverKey = certs.firstOrNull()?.publicKey ?: throw TLSException("No server certificate/public key found")
            }
            TLSHandshakeType.ServerDone -> {
                preSecret = random.generateSeed(48)
                preSecret[0] = 0x03
                preSecret[1] = 0x03 // TLS 1.2

                val secretHandshake = clientKeyExchange(random, handshakeHeader, serverKey!!, preSecret)
                handshakesPacket.writePacket(secretHandshake.copy())

                recordHeader.type = TLSRecordType.Handshake
                recordHeader.length = secretHandshake.remaining
                output.writePacket {
                    writeTLSHeader(recordHeader)
                }
                output.writePacket(secretHandshake)

                output.writePacket {
                    writeChangeCipherSpec(recordHeader)
                }

                val hash = doHash()
                val suite = cipherSuite!!
                masterSecret = masterSecret(SecretKeySpec(preSecret, suite.macName), clientRandom, serverRandom)
                preSecret.fill(0)
                preSecret = EmptyByteArray

                val finishedBody = finished(hash, masterSecret!!)
                val finished = buildPacket {
                    handshakeHeader.type = TLSHandshakeType.Finished
                    handshakeHeader.length = finishedBody.remaining
                    writeTLSHandshake(handshakeHeader)
                    writePacket(finishedBody)
                }

                handshakesPacket.writePacket(finished.copy())
                keyMaterial = keyMaterial(masterSecret!!, serverRandom + clientRandom, suite.keyStrengthInBytes, suite.macStrengthInBytes, suite.fixedIvLength)

                val cipher = encryptCipher(suite, keyMaterial, TLSRecordType.Handshake, finished.remaining, 0, 0)
                val finishedEncrypted = finished.encrypted(cipher, 0)

                output.writePacket {
                    recordHeader.type = TLSRecordType.Handshake
                    recordHeader.length = finishedEncrypted.remaining
                    writeTLSHeader(recordHeader)
                }
                output.writePacket(finishedEncrypted)

                output.flush()
            }
            else -> throw TLSException("Unsupported TLS handshake type ${handshakeHeader.type}")
        }
    }

    private fun findTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val tm = tmf.trustManagers

        return tm.first { it is X509TrustManager } as X509TrustManager
    }

    private fun clientKeyExchange(random: SecureRandom, handshake: TLSHandshakeHeader, publicKey: PublicKey, preSecret: ByteArray): ByteReadPacket {
        require(preSecret.size == 48)

        val secretPacket = WritePacket()

        secretPacket.writeEncryptedPreMasterSecret(preSecret, publicKey, random)

        handshake.type = TLSHandshakeType.ClientKeyExchange
        handshake.length = secretPacket.size

        return buildPacket {
            writeTLSHandshake(handshake)
            writePacket(secretPacket.build())
        }
    }

    private suspend fun sendClientHello() {
        with(handshakeHeader) {
            type = TLSHandshakeType.ClientHello
            suitesCount = 1
            suites[0] = TLS_RSA_WITH_AES_128_GCM_SHA256.code
            random = clientRandom.copyOf()
        }

        handshakeHeader.serverName = serverName

        val helloBody = WritePacket()
        helloBody.writeTLSClientHello(handshakeHeader)

        val packet = buildPacket {
            handshakeHeader.type = TLSHandshakeType.ClientHello
            handshakeHeader.length = helloBody.size
            writeTLSHandshake(handshakeHeader)
            writePacket(helloBody.build())
        }

        handshakesPacket.writePacket(packet.copy())

        output.writePacket {
            recordHeader.type = TLSRecordType.Handshake
            recordHeader.length = packet.remaining
            writeTLSHeader(recordHeader)
            writePacket(packet)
        }
        output.flush()
    }

    private fun doHash(): ByteArray = handshakesPacket.preview { handshakes: ByteReadPacket ->
        val digest = MessageDigest.getInstance(cipherSuite!!.hashName)!!

        val buffer = DefaultByteBufferPool.borrow()
        try {
            while (!handshakes.isEmpty) {
                val rc = handshakes.readAvailable(buffer)
                if (rc == -1) break
                buffer.flip()
                digest.update(buffer)
                buffer.clear()
            }

            return@preview digest.digest()
        } finally {
            DefaultByteBufferPool.recycle(buffer)
        }
    }

    companion object {
        private val EmptyByteArray = ByteArray(0)
    }
}