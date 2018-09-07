package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.*
import java.net.*
import java.nio.channels.*

/**
 * Socket options builder
 */
class SocketOptions private constructor(private val allOptions: MutableMap<SocketOption<*>, Any?> = HashMap()) {
    internal constructor() : this(HashMap())

    /**
     * Copy options
     */
    fun copy(): SocketOptions = SocketOptions(allOptions.toMutableMap())

    /**
     * Get particular socket [option]
     */
    operator fun <T> get(option: SocketOption<T>): T = @Suppress("UNCHECKED_CAST") (allOptions[option] as T)

    /**
     * Set particular socket [option] [value]
     */
    operator fun <T> set(option: SocketOption<T>, value: T) {
        allOptions[option] = value
    }

    /**
     * List all socket option values
     */
    fun list(): List<Pair<SocketOption<*>, Any?>> = allOptions.entries.map { Pair(it.key, it.value) }

    companion object {
        /**
         * Default socket options
         */
        @KtorExperimentalAPI
        val Empty: SocketOptions = SocketOptions()
    }
}

/**
 * Represent configurable socket
 */
interface Configurable<out T : Configurable<T>> {
    /**
     * Current socket options
     */
    var options: SocketOptions

    /**
     * Configure socket options in [block] function
     */
    fun configure(block: SocketOptions.() -> Unit): T {
        val newOptions = options.copy()
        block(newOptions)
        options = newOptions

        @Suppress("UNCHECKED_CAST")
        return this as T
    }
}

/**
 * Set TCP_NODELAY socket option to disable the Nagle algorithm.
 */
fun <T: Configurable<T>> T.tcpNoDelay(): T {
    return configure {
        this[StandardSocketOptions.TCP_NODELAY] = true
    }
}

/**
 * Start building a socket
 */
fun aSocket(selector: SelectorManager): SocketBuilder = SocketBuilder(selector, SocketOptions.Empty)

/**
 * Socket builder
 */
@Suppress("PublicApiImplicitType", "unused")
class SocketBuilder internal constructor(private val selector: SelectorManager, override var options: SocketOptions) : Configurable<SocketBuilder> {
    /**
     * Build TCP socket
     */
    fun tcp() = TcpSocketBuilder(selector, options)

    /**
     * Build UDP socket
     */
    fun udp() = UDPSocketBuilder(selector, options)
}

/**
 * TCP socket builder
 */
@Suppress("PublicApiImplicitType")
class TcpSocketBuilder internal constructor(private val selector: SelectorManager, override var options: SocketOptions) : Configurable<TcpSocketBuilder> {
    /**
     * Connect to [hostname] and [port]
     */
    suspend fun connect(hostname: String, port: Int) = connect(InetSocketAddress(hostname, port))

    /**
     * Bind server socket at [port] to listen to [hostname]
     */
    fun bind(hostname: String = "0.0.0.0", port: Int = 0) = bind(InetSocketAddress(hostname, port))

    /**
     * Connect to [remoteAddress]
     */
    suspend fun connect(remoteAddress: SocketAddress): Socket {
        return selector.buildOrClose({ openSocketChannel() }) {
            assignOptions(options)
            nonBlocking()

            SocketImpl(this, selector).apply {
                connect(remoteAddress)
            }
        }
    }

    /**
     * Bind server socket to listen to [localAddress]
     */
    fun bind(localAddress: SocketAddress? = null): ServerSocket {
        return selector.buildOrClose({ openServerSocketChannel() }) {
            assignOptions(options)
            nonBlocking()

            ServerSocketImpl(this, selector).apply {
                channel.bind(localAddress)
            }
        }
    }
}

/**
 * UDP socket builder
 */
class UDPSocketBuilder internal constructor(private val selector: SelectorManager, override var options: SocketOptions) : Configurable<UDPSocketBuilder> {
    /**
     * Bind server socket to listen to [localAddress]
     */
    fun bind(localAddress: SocketAddress? = null): BoundDatagramSocket {
        return selector.buildOrClose({ openDatagramChannel() }) {
            assignOptions(options)
            nonBlocking()

            DatagramSocketImpl(this, selector).apply {
                channel.bind(localAddress)
            }
        }
    }

    /**
     * Create a datagram socket to listen datagrams at [localAddress] and set to [remoteAddress]
     */
    fun connect(remoteAddress: SocketAddress, localAddress: SocketAddress? = null): ConnectedDatagramSocket {
        return selector.buildOrClose({ openDatagramChannel() }) {
            assignOptions(options)
            nonBlocking()

            DatagramSocketImpl(this, selector).apply {
                channel.bind(localAddress)
                channel.connect(remoteAddress)
            }
        }
    }
}

private fun NetworkChannel.assignOptions(options: SocketOptions) {
    options.list().forEach { (k, v) ->
        @Suppress("UNCHECKED_CAST")
        (setOption(k as SocketOption<Any?>, v))
    }
}

private fun SelectableChannel.nonBlocking() {
    configureBlocking(false)
}
