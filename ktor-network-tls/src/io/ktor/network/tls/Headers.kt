package io.ktor.network.tls

import kotlinx.coroutines.experimental.io.packet.ByteReadPacket

class TLSRecord(
    val type: TLSRecordType = TLSRecordType.Handshake,
    val version: TLSVersion = TLSVersion.TLS12,
    val packet: ByteReadPacket = ByteReadPacket.Empty
)

class TLSHandshake {
    var type: TLSHandshakeType = TLSHandshakeType.HelloRequest
    var packet = ByteReadPacket.Empty
}