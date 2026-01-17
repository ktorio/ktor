/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult

/**
 * Defines the overflow strategy for a channel when it reaches its capacity.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.ChannelOverflow)
 */
public enum class ChannelOverflow {
    /**
     * Suspends the sender when the channel reaches capacity.
     */
    SUSPEND,

    /**
     * Closes the channel once it reaches capacity. Existing elements remain readable.
     */
    CLOSE
}

/**
 * Thrown when a channel configured with [ChannelOverflow.CLOSE] exceeds its capacity.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.ChannelOverflowException)
 *
 * @param message the detail message describing the overflow condition
 */
public class ChannelOverflowException(message: String) : RuntimeException(message)

/**
 * A configuration for a [kotlinx.coroutines.channels.Channel].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket)
 *
 * @property capacity: channel capacity.
 * @property onOverflow: overflow strategy.
 */
public class ChannelConfig internal constructor(
    public val capacity: Int,
    public val onOverflow: ChannelOverflow,
) {
    /**
     * Whether the channel can suspend when it reaches capacity.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.ChannelConfig.canSuspend)
     */
    public val canSuspend: Boolean
        get() = onOverflow == ChannelOverflow.SUSPEND && capacity != Channel.UNLIMITED

    public companion object {
        /**
         * A configuration with unlimited buffer.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.ChannelConfig.Companion.UNLIMITED)
         */
        public val UNLIMITED: ChannelConfig =
            ChannelConfig(capacity = Channel.UNLIMITED, onOverflow = ChannelOverflow.SUSPEND)
    }
}

/**
 * A [Channel] implementation that closes when it reaches its capacity.
 */
internal class BoundedChannel<T>(
    capacity: Int,
    private val delegate: Channel<T> = createDelegate(capacity)
) : Channel<T> by delegate {

    override fun trySend(element: T): ChannelResult<Unit> {
        val result = delegate.trySend(element)
        if (!result.isSuccess && !result.isClosed) {
            close(cause = ChannelOverflowException("Channel overflowed"))
        }
        return result
    }

    companion object {
        /**
         * Creates a delegate [Channel] that triggers closure on undelivered elements due to overflow.
         */
        @OptIn(DelicateCoroutinesApi::class)
        fun <T> createDelegate(capacity: Int): Channel<T> {
            lateinit var channel: Channel<T>
            return Channel<T>(
                capacity = capacity,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
                onUndeliveredElement = {
                    if (!channel.isClosedForSend) {
                        channel.close(cause = ChannelOverflowException("Channel overflowed"))
                    }
                }
            ).also { channel = it }
        }
    }
}

/**
 * Creates a [Channel] using this configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.Channel.Factory.from)
 */
@OptIn(DelicateCoroutinesApi::class)
@InternalAPI
public fun <T> Channel.Factory.from(config: ChannelConfig): Channel<T> = with(config) {
    when {
        capacity == Channel.UNLIMITED -> Channel(capacity = Channel.UNLIMITED)
        onOverflow == ChannelOverflow.SUSPEND -> Channel(capacity, onBufferOverflow = BufferOverflow.SUSPEND)
        onOverflow == ChannelOverflow.CLOSE -> BoundedChannel(capacity)
        else -> error("Unsupported channel config.")
    }
}

/**
 * Configuration for incoming and outgoing WebSocket frame channels.
 *
 * Use this to control backpressure behavior by limiting channel capacities
 * and specifying overflow strategies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.IOChannelsConfig)
 *
 * @see IOChannelsConfigBuilder
 */
public class IOChannelsConfig internal constructor(
    public val incoming: ChannelConfig,
    public val outgoing: ChannelConfig
) {
    public companion object {
        /**
         * A configuration with unlimited buffer for both incoming and outgoing channels.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.IOChannelsConfig.Companion.UNLIMITED)
         */
        public val UNLIMITED: IOChannelsConfig =
            IOChannelsConfig(incoming = ChannelConfig.UNLIMITED, outgoing = ChannelConfig.UNLIMITED)
    }
}

/**
 * Builder for configuring incoming and outgoing WebSocket frame channels.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.IOChannelsConfigBuilder)
 *
 * @see IOChannelsConfig
 */
public class IOChannelsConfigBuilder @InternalAPI public constructor() {
    /**
     * Configuration for the incoming channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.IOChannelsConfigBuilder.incoming)
     */
    public var incoming: ChannelConfig = ChannelConfig.UNLIMITED

    /**
     * Configuration for the outgoing channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.IOChannelsConfigBuilder.outgoing)
     */
    public var outgoing: ChannelConfig = ChannelConfig.UNLIMITED

    /**
     * A configuration with unlimited buffer.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.unlimited)
     */
    public fun unlimited(): ChannelConfig = ChannelConfig.UNLIMITED

    /**
     * A configuration with a specific [capacity] and [onOverflow] strategy.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.buffered)
     */
    public fun bounded(capacity: Int, onOverflow: ChannelOverflow = ChannelOverflow.SUSPEND): ChannelConfig =
        ChannelConfig(capacity, onOverflow)

    /**
     * Builds an [IOChannelsConfig] from this builder.
     */
    public fun build(): IOChannelsConfig = IOChannelsConfig(incoming, outgoing)
}

/**
 * Creates an [IOChannelsConfig] using the provided configuration block.
 * Should be used only to customize manually created raw websocket sessions.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.websocket.IOChannelsConfig)
 *
 * @param configure the configuration block to apply
 * @return the configured [IOChannelsConfig]
 */
public fun IOChannelsConfig(configure: IOChannelsConfigBuilder.() -> Unit): IOChannelsConfig {
    @OptIn(InternalAPI::class)
    return IOChannelsConfigBuilder().apply(configure).build()
}
