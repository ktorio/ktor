/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.tls.NetworkRole.*
import io.ktor.network.tls.cipher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.*
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.*

/**
 * Handles the internal plumbing for the TLS handshake.
 *
 * @property rawInput The input channel to read the raw data from the connection.
 * @property rawOutput The output channel to write the raw data to the connection.
 * @property config The TLS configuration parameters.
 * @property coroutineContext The coroutine context used for concurrency.
 */
internal class TLSSocketAdaptor(
    private val rawInput: ByteReadChannel,
    private val rawOutput: ByteWriteChannel,
    private val config: TLSConfig,
    override val coroutineContext: CoroutineContext
) : TLSConfigJvm by config, CoroutineScope, Closeable {

    companion object {
        val DUMMY_KEY = SecretKeySpec(ByteArray(1), "")
        val Skip: RecordOp = { it }
    }

    /**
     * Performs the TLS handshake in accordance with the provided algorithm.
     *
     * This must be executed before wrapping the socket.
     */
    suspend fun negotiate(algorithm: TLSHandshakeAlgorithm) =
        with(algorithm) {
            negotiate()
            handshakes.cancel()
            handshakeComplete = true
        }

    /**
     * Wraps a raw socket to provide encryption over data sent and received.
     */
    fun wrap(socket: Socket): Socket {
        check(handshakeComplete)
        return TLSSocket(version, input, output, socket, coroutineContext)
    }

    override fun close() {
        input.cancel()
        output.close()
    }

    /**
     * Keeps a log of TLS handshakes which is used for the "Finish" message checksum.
     */
    internal val digest = Digest()
    private val otherRole = when (config.role) {
        SERVER -> CLIENT
        CLIENT -> SERVER
    }
    private fun onReceiveHandshake(type: TLSHandshakeType, recordBody: ByteReadPacket) =
        onHandshake(type, otherRole, recordBody)
    private fun onSendHandshake(type: TLSHandshakeType, recordBody: ByteReadPacket) =
        onHandshake(type, config.role, recordBody)
    private fun onHandshake(type: TLSHandshakeType, role: NetworkRole, recordBody: ByteReadPacket) {
        config.onHandshake?.invoke(this, type, role)
        digest.update(recordBody)
    }

    /**
     * Cipher suite and server / client seeds which are established in the "Hello" exchange
     * between the client and server.
     */
    internal lateinit var cipherSuite: CipherSuite
    internal lateinit var serverSeed: ByteArray
    internal lateinit var clientSeed: ByteArray

    fun initCipherSuite(clientHello: TLSClientHello, serverHello: TLSServerHello) {
        clientSeed = clientHello.clientSeed
        serverSeed = serverHello.serverSeed
        cipherSuite = serverHello.cipherSuite
    }

    /**
     * Master secret, derived from the "pre-secret", which is derived from the key exchange.
     */
    @Volatile
    internal var masterSecret: SecretKeySpec = DUMMY_KEY
        set(value) {
            field = value
            masterSecretReady.unlock()
        }

    private val masterSecretReady = Mutex(locked = true)

    /**
     * Cipher used for the encryption of TLS records after the handshake is complete and the
     * ChangeCipherSpec message is sent from peer.
     */
    private val cipher: Deferred<TLSCipher> = async {
        masterSecretReady.withLock {
            val keyMaterial = with(cipherSuite) {
                keyMaterial(
                    masterSecret,
                    serverSeed + clientSeed,
                    keyStrengthInBytes,
                    macStrengthInBytes,
                    fixedIvLength
                )
            }
            TLSCipher.fromSuite(cipherSuite, keyMaterial, config.role)
        }
    }

    private var handshakeComplete = false

    internal fun generateSeed(): ByteArray = ByteArray(32).also { seed ->
        config.random.nextBytes(seed)
        val unixTime = (System.currentTimeMillis() / 1000L)
        seed[0] = (unixTime shr 24).toByte()
        seed[1] = (unixTime shr 16).toByte()
        seed[2] = (unixTime shr 8).toByte()
        seed[3] = (unixTime shr 0).toByte()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val input: ReceiveChannel<TLSRecord> = produce(CoroutineName("cio-tls-parser")) {
        var recordOp = Skip
        try {
            loop@ while (true) {
                val rawRecord = TLSRecord.read(rawInput)
                val record = recordOp(rawRecord)
                val packet = record.packet

                when (record.type) {
                    TLSRecordType.Alert -> {
                        val level = TLSAlertLevel.byCode(packet.readByte().toInt())
                        val code = TLSAlertType.byCode(packet.readByte().toInt())

                        if (code == TLSAlertType.CloseNotify) return@produce
                        val cause = TLSAlertException("Received alert during handshake. Level: $level, code: $code")

                        channel.close(cause)
                        return@produce
                    }

                    TLSRecordType.ChangeCipherSpec -> {
                        check(recordOp === Skip)
                        val flag = packet.readByte()
                        if (flag != 1.toByte()) {
                            throw TLSValidationException("Expected flag: 1, received $flag in ChangeCipherSpec")
                        }
                        recordOp = cipher.await().let { cipher -> cipher::decrypt }
                        continue@loop
                    }

                    else -> {}
                }

                channel.send(TLSRecord(record.type, version, packet = packet))
            }
        } catch (cause: ClosedReceiveChannelException) {
            channel.close()
        } catch (cause: Throwable) {
            channel.close(cause)
            // Remote server closed connection
        } finally {
            output.close()
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    internal val output: SendChannel<TLSRecord> = actor(CoroutineName("cio-tls-encoder")) {
        var recordOp = Skip

        try {
            for (rawRecord in channel) {
                try {
                    val record = recordOp(rawRecord)
                    if (rawRecord.type == TLSRecordType.ChangeCipherSpec) {
                        recordOp = cipher.await().let { cipher -> cipher::encrypt }
                    }

                    rawOutput.writeRecord(record)
                } catch (cause: Throwable) {
                    channel.close(cause)
                }
            }
        } finally {
            try {
                rawOutput.writeRecord(
                    recordOp(
                        TLSRecord(
                            type = TLSRecordType.Alert,
                            version = version,
                            packet = buildPacket {
                                writeByte(TLSAlertLevel.WARNING.code.toByte())
                                writeByte(TLSAlertType.CloseNotify.code.toByte())
                            }
                        )
                    )
                )
            } finally {
                rawOutput.close()
            }
        }
    }

    /**
     * Inbound handshake messages; ignores HelloRequest, appends to digest automatically.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val handshakes: ReceiveChannel<TLSHandshakeMessage> = produce(CoroutineName("cio-tls-handshake")) {
        val nextRecord: suspend () -> TLSRecord = config.handshakeTimeoutMillis?.let { timeout ->
            { withTimeout(timeout) { input.receive() } }
        } ?: {
            input.receive()
        }
        try {
            while (true) {
                val record = nextRecord()
                if (record.type != TLSRecordType.Handshake) {
                    record.packet.release()
                    error("TLS handshake expected, got ${record.type}")
                }

                val packet = record.packet

                while (packet.isNotEmpty) {
                    val handshake = TLSHandshakeMessage.read(packet)
                    if (handshake.type == TLSHandshakeType.HelloRequest) continue
                    onReceiveHandshake(handshake.type, handshake.recordPacketCopy())
                    channel.send(handshake)
                }
            }
        } catch (cause: ClosedReceiveChannelException) {
            channel.close()
        } catch (cause: Throwable) {
            channel.close(cause)
            // Remote server closed connection
        }
    }

    /**
     * Creates TLSRecord from handshake and automatically includes it in the digest.
     */
    internal suspend fun SendChannel<TLSRecord>.send(
        handshakeType: TLSHandshakeType,
        block: BytePacketBuilder.() -> Unit = {}
    ) {
        val handshakeBody = buildPacket(block = block)
        val recordBody = buildPacket {
            writeTLSHandshakeType(handshakeType, handshakeBody.remaining.toInt())
            writePacket(handshakeBody)
        }
        onSendHandshake(handshakeType, recordBody.copy())

        val element = TLSRecord(TLSRecordType.Handshake, version, recordBody)
        try {
            send(element)
        } catch (cause: Throwable) {
            element.packet.release()
            throw cause
        }
    }
}

private typealias RecordOp = (TLSRecord) -> TLSRecord
