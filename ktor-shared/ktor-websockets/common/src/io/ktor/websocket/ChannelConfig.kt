/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * A configuration for a [kotlinx.coroutines.channels.Channel].
 * The implementation is not necessarily the default one, so don't rely on it.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ChannelConfig)
 *
 * @property capacity: channel capacity.
 * @property onBufferOverflow: strategy to use on buffer overflow.
 * @property onUndeliveredElement: handler for undelivered elements.
 */
public data class ChannelConfig<in E>(
    public val capacity: Int,
    public val onBufferOverflow: BufferOverflow,
    public val onUndeliveredElement: ((E) -> Unit)? = null
) {
    /**
     * Creates a [kotlinx.coroutines.channels.Channel] using this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ChannelConfig.toChannel)
     */
    public fun <T : E> toChannel(): Channel<T> {
        return Channel(capacity, onBufferOverflow, onUndeliveredElement)
    }

    public companion object {
        /**
         * A configuration with no buffer.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ChannelConfig.Companion.NO_BUFFER)
         */
        public val NO_BUFFER: ChannelConfig<Any> =
            ChannelConfig(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)

        /**
         * A configuration with a small buffer.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ChannelConfig.Companion.SMALL_BUFFER)
         */
        public val SMALL_BUFFER: ChannelConfig<Any> =
            ChannelConfig(capacity = 8, onBufferOverflow = BufferOverflow.SUSPEND)

        /**
         * A configuration with unlimited buffer.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ChannelConfig.Companion.UNLIMITED)
         */
        public val UNLIMITED: ChannelConfig<Any> =
            ChannelConfig(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)

        /**
         * Creates a configuration with the specified [capacity] and [kotlinx.coroutines.channels.BufferOverflow.SUSPEND] strategy.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ChannelConfig.Companion.withCapacity)
         */
        public fun withCapacity(capacity: Int): ChannelConfig<Any> =
            ChannelConfig(capacity, onBufferOverflow = BufferOverflow.SUSPEND)
    }
}
