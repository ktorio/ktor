package io.ktor.utils.io.core

import kotlin.contracts.*

/**
 * Build a byte packet in [block] lambda. Creates a temporary builder and releases it in case of failure
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalContracts::class)
public inline fun buildPacket(block: BytePacketBuilder.() -> Unit): ByteReadPacket {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val builder = kotlinx.io.Buffer()
    block(builder)
    return builder
}
