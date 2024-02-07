/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.cipher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import javax.crypto.spec.*
import kotlin.coroutines.*

internal typealias TLSHandshakeCtx = TLSHandshakeConnector.TLSHandshakeContext

internal class TLSHandshakeConnector(
    private val rawInput: ByteReadChannel,
    private val rawOutput: ByteWriteChannel,
    internal val config: TLSConfig,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {

    private val digest = Digest()
    private var connection: TLSHandshakeContext? = null

    val cipherSuites get() = config.cipherSuites
    val serverName get() = config.serverName

    fun generateSeed(): ByteArray = ByteArray(32).also { seed ->
        config.random.nextBytes(seed)
        val unixTime = (System.currentTimeMillis() / 1000L)
        seed[0] = (unixTime shr 24).toByte()
        seed[1] = (unixTime shr 16).toByte()
        seed[2] = (unixTime shr 8).toByte()
        seed[3] = (unixTime shr 0).toByte()
    }

    suspend fun sendRawRecord(handshakeType: TLSHandshakeType, block: BytePacketBuilder.() -> Unit) {
        rawOutput.writeRecord(buildRecord(handshakeType, block))
    }

    suspend fun readRawRecord(): TLSRecord {
        return rawInput.readTLSRecord()
    }

    private fun buildRecord(handshakeType: TLSHandshakeType, block: BytePacketBuilder.() -> Unit): TLSRecord {
        val handshakeBody = buildPacket(block = block)

        val recordBody = buildPacket {
            writeTLSHandshakeType(handshakeType, handshakeBody.remaining.toInt())
            writePacket(handshakeBody)
        }

        digest.update(recordBody)
        return TLSRecord(TLSRecordType.Handshake, packet = recordBody)
    }

    suspend fun connect(
        cipherSuite: CipherSuite,
        serverSeed: ByteArray,
        clientSeed: ByteArray,
        exchange: suspend TLSHandshakeContext.() -> Unit
    ) {
        connection = TLSHandshakeContext(
            cipherSuite,
            serverSeed,
            clientSeed,
            config,
            digest
        ).also {
            it.exchange()
        }
    }

    fun getIOChannels(): Pair<ReceiveChannel<TLSRecord>, SendChannel<TLSRecord>> =
        connection!!.let { it.input to it.output }

    internal inner class TLSHandshakeContext(
        val cipherSuite: CipherSuite,
        val serverSeed: ByteArray,
        val clientSeed: ByteArray,
        val config: TLSConfig,
        val digest: Digest,
    ) {

        @Volatile
        internal lateinit var masterSecret: SecretKeySpec

        private val keyMaterial: ByteArray by lazy {
            with(cipherSuite) {
                keyMaterial(
                    masterSecret,
                    serverSeed + clientSeed,
                    keyStrengthInBytes,
                    macStrengthInBytes,
                    fixedIvLength
                )
            }
        }

        private val cipher: TLSCipher by lazy {
            TLSCipher.fromSuite(cipherSuite, keyMaterial)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        val input: ReceiveChannel<TLSRecord> = produce(CoroutineName("cio-tls-parser")) {
            var useCipher = false
            try {
                loop@ while (true) {
                    val rawRecord = rawInput.readTLSRecord()
                    val record = if (useCipher) cipher.decrypt(rawRecord) else rawRecord
                    val packet = record.packet

                    when (record.type) {
                        TLSRecordType.Alert -> {
                            val level = TLSAlertLevel.byCode(packet.readByte().toInt())
                            val code = TLSAlertType.byCode(packet.readByte().toInt())

                            if (code == TLSAlertType.CloseNotify) return@produce
                            val cause = TLSException("Received alert during handshake. Level: $level, code: $code")

                            channel.close(cause)
                            return@produce
                        }

                        TLSRecordType.ChangeCipherSpec -> {
                            check(!useCipher)
                            val flag = packet.readByte()
                            if (flag != 1.toByte()) {
                                throw TLSException("Expected flag: 1, received $flag in ChangeCipherSpec")
                            }
                            useCipher = true
                            continue@loop
                        }

                        else -> {
                        }
                    }

                    channel.send(TLSRecord(record.type, packet = packet))
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
        val output: SendChannel<TLSRecord> = actor(CoroutineName("cio-tls-encoder")) {
            var useCipher = false

            try {
                for (rawRecord in channel) {
                    try {
                        val record = if (useCipher) cipher.encrypt(rawRecord) else rawRecord
                        if (rawRecord.type == TLSRecordType.ChangeCipherSpec) useCipher = true

                        rawOutput.writeRecord(record)
                    } catch (cause: Throwable) {
                        channel.close(cause)
                    }
                }
            } finally {
                rawOutput.writeRecord(
                    TLSRecord(
                        TLSRecordType.Alert,
                        packet = buildPacket {
                            writeByte(TLSAlertLevel.WARNING.code.toByte())
                            writeByte(TLSAlertType.CloseNotify.code.toByte())
                        }
                    )
                )

                rawOutput.close()
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        val handshakes: ReceiveChannel<TLSHandshake> = produce(CoroutineName("cio-tls-handshake")) {
            try {
                while (true) {
                    val record = input.receive()
                    if (record.type != TLSRecordType.Handshake) {
                        record.packet.release()
                        error("TLS handshake expected, got ${record.type}")
                    }

                    val packet = record.packet

                    while (packet.isNotEmpty) {
                        val handshake = TLSHandshake.read(packet)
                        if (handshake.type == TLSHandshakeType.HelloRequest) continue
                        if (handshake.type != TLSHandshakeType.Finished) {
                            digest += handshake
                        }

                        channel.send(handshake)

                        if (handshake.type == TLSHandshakeType.Finished) {
                            packet.release()
                            return@produce
                        }
                    }
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

        suspend fun sendRecord(handshakeType: TLSHandshakeType, block: BytePacketBuilder.() -> Unit = {}) {
            val handshakeBody = buildPacket(block = block)

            val recordBody = buildPacket {
                writeTLSHandshakeType(handshakeType, handshakeBody.remaining.toInt())
                writePacket(handshakeBody)
            }

            digest.update(recordBody)
            val element = TLSRecord(TLSRecordType.Handshake, packet = recordBody)
            try {
                output.send(element)
            } catch (cause: Throwable) {
                element.packet.release()
                throw cause
            }
        }
    }

}
