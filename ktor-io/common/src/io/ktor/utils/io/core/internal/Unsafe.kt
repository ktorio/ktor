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
public annotation class DangerousInternalIoApi

@DangerousInternalIoApi
public fun ByteReadPacket.`$unsafeAppend$`(builder: BytePacketBuilder) {
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

@DangerousInternalIoApi
public fun Input.prepareReadFirstHead(minSize: Int): ChunkBuffer? = prepareReadHead(minSize)

@DangerousInternalIoApi
public fun Input.completeReadHead(current: ChunkBuffer) {
    when {
        current === this -> return
        !current.canRead() -> ensureNext(current)
        current.endGap < Buffer.ReservedSize -> fixGapAfterRead(current)
        else -> headPosition = current.readPosition
    }
}

@DangerousInternalIoApi
public fun Input.prepareReadNextHead(current: ChunkBuffer): ChunkBuffer? {
    if (current === this) {
        return if (canRead()) this else null
    }
    
    return ensureNextHead(current)
}

@DangerousInternalIoApi
public fun Output.prepareWriteHead(capacity: Int, current: ChunkBuffer?): ChunkBuffer {
    if (current != null) {
        afterHeadWrite()
    }
    return prepareWriteHead(capacity)
}

@JvmField
@SharedImmutable
internal val EmptyByteArray = ByteArray(0)

