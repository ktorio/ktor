package io.ktor.utils.io.core

import kotlin.contracts.*

@PublishedApi
internal inline fun Input.read(n: Int = 1, block: (Buffer) -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val buffer = prepareRead(n) ?: prematureEndOfStream(n)
    val positionBefore = buffer.readPosition
    try {
        block(buffer)
    } finally {
        val positionAfter = buffer.readPosition
        if (positionAfter < positionBefore) {
            throw IllegalStateException("Buffer's position shouldn't be rewinded")
        }
        if (positionAfter == buffer.writePosition) {
            ensureNext(buffer)
        } else {
            headPosition = positionAfter
        }
    }
}
