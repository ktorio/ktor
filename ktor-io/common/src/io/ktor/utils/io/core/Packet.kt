@file:Suppress("RedundantModalityModifier")

package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.pool.*

public expect class EOFException(message: String) : IOException

/**
 * For streaming input it should be [Input.endOfInput] instead.
 */
@Deprecated("Use endOfInput property instead", ReplaceWith("endOfInput"))
public inline val Input.isEmpty: Boolean
    get() = endOfInput

/**
 * For streaming input there is no reliable way to detect it without triggering bytes population from the underlying
 * source. Consider using [Input.endOfInput] or use [ByteReadPacket] instead.
 */
@Deprecated(
    "This makes no sense for streaming inputs. Some use-cases are covered by endOfInput property",
    ReplaceWith("!endOfInput")
)
public val Input.isNotEmpty: Boolean
    get() {
        if (endOfInput) return false
        prepareReadFirstHead(1)?.let { found ->
            completeReadHead(found)
            return true
        }
        return false
    }

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public inline val ByteReadPacket.isEmpty: Boolean
    get() = endOfInput

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public inline val ByteReadPacket.isNotEmpty: Boolean
    get() = !endOfInput
