package io.ktor.utils.io.core

import kotlin.contracts.*

public expect val PACKET_MAX_COPY_SIZE: Int

/**
 * Build a byte packet in [block] lambda. Creates a temporary builder and releases it in case of failure
 */
@OptIn(ExperimentalContracts::class)
public inline fun buildPacket(block: BytePacketBuilder.() -> Unit): ByteReadPacket {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val builder = BytePacketBuilder()
    try {
        block(builder)
        return builder.build()
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

/**
 * Discard all written bytes and prepare to build another packet.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun BytePacketBuilder.reset() {
    release()
}
