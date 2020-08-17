@file:Suppress("KDocMissingDocumentation")

package io.ktor.utils.io.core.internal

import io.ktor.utils.io.core.*
import kotlin.jvm.*
import kotlin.native.concurrent.*

/**
 * API marked with this annotation is internal and extremely fragile and not intended to be used by library users.
 * Such API could be changed without notice including rename, removal and behaviour change.
 * Also using API marked with this annotation could cause data loss or any other damage.
 */
@Suppress("DEPRECATION")
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Experimental(level = Experimental.Level.ERROR)
annotation class DangerousInternalIoApi

@DangerousInternalIoApi
fun ByteReadPacket.`$unsafeAppend$`(builder: BytePacketBuilder) {
    val builderHead = builder.stealAll() ?: return
    val builderSize = builder.size

    if (builderSize <= PACKET_MAX_COPY_SIZE && builderHead.next == null && tryWriteAppend(builderHead)) {
        builder.afterBytesStolen()
        return
    }

    append(builderHead)
}

internal fun ByteReadPacket.unsafeAppend(builder: BytePacketBuilder): Int {
    val builderSize = builder.size
    val builderHead = builder.stealAll() ?: return 0

    if (builderSize <= PACKET_MAX_COPY_SIZE && builderHead.next == null && tryWriteAppend(builderHead)) {
        builder.afterBytesStolen()
        return builderSize
    }

    append(builderHead)
    return builderSize
}


@Suppress("DEPRECATION", "UNUSED")
@JvmName("prepareReadFirstHead")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Input.prepareReadFirstHeadOld(minSize: Int): IoBuffer? {
    return prepareReadFirstHead(minSize) as IoBuffer?
}

@DangerousInternalIoApi
fun Input.prepareReadFirstHead(minSize: Int): ChunkBuffer? {
    if (this is AbstractInput) {
        return prepareReadHead(minSize)
    }
    if (this is ChunkBuffer) {
        return if (canRead()) this else null
    }

    return prepareReadHeadFallback(minSize)
}

private fun Input.prepareReadHeadFallback(minSize: Int): ChunkBuffer? {
    if (endOfInput) return null

    val buffer = ChunkBuffer.Pool.borrow()
    val copied = peekTo(
        buffer.memory,
        buffer.writePosition.toLong(),
        0L,
        minSize.toLong(),
        buffer.writeRemaining.toLong()
    ).toInt()
    buffer.commitWritten(copied)

    if (copied < minSize) {
        prematureEndOfStream(minSize)
    }

    return buffer
}

@Suppress("UNUSED", "DEPRECATION")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Input.completeReadHead(current: IoBuffer) {
    completeReadHead(current)
}

@DangerousInternalIoApi
fun Input.completeReadHead(current: ChunkBuffer) {
    if (current === this) {
        return
    }
    if (this is AbstractInput) {
        if (!current.canRead()) {
            ensureNext(current)
        } else if (current.endGap < Buffer.ReservedSize) {
            fixGapAfterRead(current)
        } else {
            headPosition = current.readPosition
        }
        return
    }

    completeReadHeadFallback(current)
}

private fun Input.completeReadHeadFallback(current: ChunkBuffer) {
    val discardAmount = current.capacity - current.writeRemaining - current.readRemaining
    discardExact(discardAmount)
    current.release(ChunkBuffer.Pool)
}

@Suppress("DEPRECATION", "UNUSED")
@JvmName("prepareReadNextHead")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Input.prepareReadNextHeadOld(current: IoBuffer): IoBuffer? {
    return prepareReadNextHead(current) as IoBuffer?
}

@DangerousInternalIoApi
fun Input.prepareReadNextHead(current: ChunkBuffer): ChunkBuffer? {
    if (current === this) {
        return if (canRead()) this else null
    }
    if (this is AbstractInput) {
        return ensureNextHead(current)
    }

    return prepareNextReadHeadFallback(current)
}

private fun Input.prepareNextReadHeadFallback(current: ChunkBuffer): ChunkBuffer? {
    val discardAmount = current.capacity - current.writeRemaining - current.readRemaining
    discardExact(discardAmount)
    current.resetForWrite()

    if (endOfInput || peekTo(current) <= 0) {
        current.release(ChunkBuffer.Pool)
        return null
    }

    return current
}

@Suppress("DEPRECATION", "UNUSED")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Output.prepareWriteHead(capacity: Int, current: IoBuffer?): IoBuffer {
    return prepareWriteHead(capacity, current) as IoBuffer
}

@DangerousInternalIoApi
fun Output.prepareWriteHead(capacity: Int, current: ChunkBuffer?): ChunkBuffer {
    if (this is AbstractOutput) {
        if (current != null) {
            afterHeadWrite()
        }
        return prepareWriteHead(capacity)
    }

    return prepareWriteHeadFallback(current)
}

private fun Output.prepareWriteHeadFallback(current: ChunkBuffer?): ChunkBuffer {
    if (current != null) {
        writeFully(current)
        current.resetForWrite()
        return current
    }

    return ChunkBuffer.Pool.borrow()
}

@Suppress("DEPRECATION", "UNUSED")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Output.afterHeadWrite(current: IoBuffer) {
    return afterHeadWrite(current)
}

@DangerousInternalIoApi
fun Output.afterHeadWrite(current: ChunkBuffer) {
    if (this is AbstractOutput) {
        return afterHeadWrite()
    }

    afterWriteHeadFallback(current)
}

@JvmField
@SharedImmutable
internal val EmptyByteArray = ByteArray(0)

private fun Output.afterWriteHeadFallback(current: ChunkBuffer) {
    writeFully(current)
    current.release(ChunkBuffer.Pool)
}
