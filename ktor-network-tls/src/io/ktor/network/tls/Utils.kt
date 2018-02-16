package io.ktor.network.tls

import kotlinx.io.core.*

internal fun ByteReadPacket.duplicate(): Pair<ByteReadPacket, ByteReadPacket> {
    if (this.isEmpty) return ByteReadPacket.Empty to ByteReadPacket.Empty
    return this to copy()
}
