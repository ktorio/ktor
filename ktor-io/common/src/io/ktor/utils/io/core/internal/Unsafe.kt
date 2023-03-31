@file:Suppress("KDocMissingDocumentation")

package io.ktor.utils.io.core.internal

import io.ktor.utils.io.core.*
import kotlin.jvm.*

/**
 * API marked with this annotation is internal and extremely fragile and not intended to be used by library users.
 * Such API could be changed without a notice including rename, removal and behavior change.
 * Also, using an API marked with this annotation could cause data loss or any other damage.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class DangerousInternalIoApi

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

@Suppress("DEPRECATION")
@PublishedApi
internal fun Input.prepareReadFirstHead(minSize: Int): ChunkBuffer? = prepareReadHead(minSize)

@PublishedApi
@Suppress("DEPRECATION")
internal fun Input.completeReadHead(current: ChunkBuffer) {
    when {
        current === this -> return
        !current.canRead() -> ensureNext(current)
        current.endGap < Buffer.ReservedSize -> fixGapAfterRead(current)
        else -> headPosition = current.readPosition
    }
}

@Suppress("DEPRECATION")
@PublishedApi
internal fun Input.prepareReadNextHead(current: ChunkBuffer): ChunkBuffer? {
    return ensureNextHead(current)
}

@Suppress("DEPRECATION")
internal fun Output.prepareWriteHead(capacity: Int, current: ChunkBuffer?): ChunkBuffer {
    if (current != null) {
        afterHeadWrite()
    }
    return prepareWriteHead(capacity)
}

@JvmField
internal val EmptyByteArray = ByteArray(0)
