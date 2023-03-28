// ktlint-disable filename
/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import at.favre.lib.crypto.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.consts.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import net.luminis.tls.*
import net.luminis.tls.extension.*
import net.luminis.tls.handshake.*
import java.nio.*
import kotlin.experimental.*

internal actual class TLSServerComponent(
    private val communicationProvider: ProtocolCommunicationProvider,
) : TLSComponent, TlsStatusEventHandler, ServerMessageSender {
    private lateinit var engine: TlsServerEngine
    private lateinit var originalDcid: ByteArray

    private val messageParser = TlsMessageParser { bytes, _ ->
        QUICServerTLSExtension.fromBytes(ByteReadPacket(bytes), true)
    }

    private val clientInitialKeys = CompletableDeferred<CryptoKeys>()
    private val serverInitialKeys = CompletableDeferred<CryptoKeys>()

    private val clientHandshakeKeys = CompletableDeferred<CryptoKeys>()
    private val serverHandshakeKeys = CompletableDeferred<CryptoKeys>()

    private val client1RTTKeys = CompletableDeferred<CryptoKeys>()
    private val server1RTTKeys = CompletableDeferred<CryptoKeys>()

    fun initEngine(engine: TlsServerEngine) {
        this.engine = engine
    }

    actual suspend fun acceptOriginalDcid(originalDcid: ConnectionID) {
        this.originalDcid = originalDcid.value
        calculateInitialKeys()
    }

    actual suspend fun acceptInitialHandshake(cryptoFramePayload: ByteArray) {
        processCryptoFrame(cryptoFramePayload, ProtectionKeysType.None)
    }

    actual suspend fun finishHandshake(cryptoFramePayload: ByteArray) {
        processCryptoFrame(cryptoFramePayload, ProtectionKeysType.Handshake)
    }

    override suspend fun headerProtectionMask(sample: ByteArray, level: EncryptionLevel, isDecrypting: Boolean): Long {
        val keys = when {
            isDecrypting -> when (level) {
                EncryptionLevel.Initial -> clientInitialKeys
                EncryptionLevel.Handshake -> clientHandshakeKeys
                EncryptionLevel.AppData -> client1RTTKeys
            }

            else -> when (level) {
                EncryptionLevel.Initial -> serverInitialKeys
                EncryptionLevel.Handshake -> serverHandshakeKeys
                EncryptionLevel.AppData -> server1RTTKeys
            }
        }.await()

        return keys.headerProtectionMask(sample)
    }

    override suspend fun decrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        level: EncryptionLevel,
    ): ByteArray {
        val keys = when (level) {
            EncryptionLevel.Initial -> clientInitialKeys
            EncryptionLevel.Handshake -> clientHandshakeKeys
            EncryptionLevel.AppData -> client1RTTKeys
        }.await()

        return decrypt(payload, associatedData, packetNumber, keys)
    }

    override suspend fun encrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        level: EncryptionLevel,
    ): ByteArray {
        val keys = when (level) {
            EncryptionLevel.Initial -> serverInitialKeys
            EncryptionLevel.Handshake -> serverHandshakeKeys
            EncryptionLevel.AppData -> server1RTTKeys
        }.await()

        return encrypt(payload, associatedData, packetNumber, keys)
    }

    private fun decrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        keys: CryptoKeys,
    ): ByteArray {
        return keys.decrypt(payload, associatedData, createNonce(keys, packetNumber))
    }

    private fun encrypt(
        payload: ByteArray,
        associatedData: ByteArray,
        packetNumber: Long,
        keys: CryptoKeys,
    ): ByteArray {
        return keys.encrypt(payload, associatedData, createNonce(keys, packetNumber))
    }

    private fun createNonce(keys: CryptoKeys, packetNumber: Long): ByteArray {
        return ByteBuffer.allocate(keys.iv.size).apply {
            for (i in 0 until keys.iv.size - 8) {
                put(0x00)
            }
            putLong(packetNumber)
        }.array().let { buffer ->
            ByteArray(buffer.size) { i ->
                buffer[i] xor keys.iv[i]
            }
        }
    }

    private fun calculateInitialKeys() {
        val keys = HKDF.fromHmacSha256().extract(TLSConstants.V1.SALT, originalDcid)

        clientInitialKeys.complete(CryptoKeys.initial(keys, QUICVersion.V1, isServer = false))
        serverInitialKeys.complete(CryptoKeys.initial(keys, QUICVersion.V1, isServer = true))
    }

    // Helper methods

    private suspend fun processCryptoFrame(payload: ByteArray, keysType: ProtectionKeysType) {
        try {
            messageParser.parseAndProcessHandshakeMessage(ByteBuffer.wrap(payload), engine, keysType)
        } catch (e: TlsProtocolException) {
            communicationProvider.raiseError(e.toError())
        }
        // we do not catch IOException as it is only could be thrown in `send` methods of this class, which it isn't
    }

    override fun earlySecretsKnown() {
    }

    override fun handshakeSecretsKnown() {
        clientHandshakeKeys.complete(CryptoKeys(engine.clientHandshakeTrafficSecret, QUICVersion.V1))
        serverHandshakeKeys.complete(CryptoKeys(engine.serverHandshakeTrafficSecret, QUICVersion.V1))
    }

    override fun handshakeFinished() {
        client1RTTKeys.complete(CryptoKeys(engine.clientApplicationTrafficSecret, QUICVersion.V1))
        server1RTTKeys.complete(CryptoKeys(engine.serverApplicationTrafficSecret, QUICVersion.V1))
    }

    override fun newSessionTicketReceived(newSessionTicket: NewSessionTicket?) {
        // nothing for server to do here
    }

    override fun extensionsReceived(extensions: MutableList<Extension>?) {
        val peerTransportParameters: TransportParameters = extensions
            ?.filterIsInstance<QUICServerTLSExtension>()
            ?.single()
            ?.transportParameters
            ?: transportParameters() // todo error or default?

        val endpointTransportParameters = communicationProvider.getTransportParameters(peerTransportParameters)

        engine.addServerExtensions(QUICServerTLSExtension(endpointTransportParameters, true))
    }

    override fun isEarlyDataAccepted(): Boolean {
        return false
    }

    override fun send(message: ServerHello?) = sendMessage(message, isHandshakeMessage = false)

    override fun send(message: EncryptedExtensions?) = sendMessage(message, isHandshakeMessage = true)

    override fun send(message: CertificateMessage?) = sendMessage(message, isHandshakeMessage = true)

    override fun send(message: CertificateVerifyMessage?) = sendMessage(message, isHandshakeMessage = true)

    override fun send(message: FinishedMessage?) = sendMessage(message, isHandshakeMessage = true, flush = true)

    override fun send(message: NewSessionTicketMessage?) = TODO("Not yet implemented")

    private fun sendMessage(message: HandshakeMessage?, isHandshakeMessage: Boolean, flush: Boolean = false) {
        message ?: return

        communicationProvider.sendCryptoFrame(message.bytes, isHandshakeMessage, flush)
    }
}
