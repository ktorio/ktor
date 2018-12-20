package io.ktor.network.sockets

/**
 * Socket options builder
 */
@UseExperimental(ExperimentalUnsignedTypes::class)
sealed class SocketOptions(
    @Suppress("KDocMissingDocumentation") protected val customOptions: MutableMap<Any, Any?>
) {
    /**
     * Copy options
     */
    internal abstract fun copy(): SocketOptions

    @Suppress("KDocMissingDocumentation")
    protected open fun copyCommon(from: SocketOptions) {
        typeOfService = from.typeOfService
        reuseAddress = from.reuseAddress
        reusePort = from.reusePort
    }

    internal fun peer(): PeerSocketOptions {
        return PeerSocketOptions(HashMap(customOptions)).apply {
            copyCommon(this@SocketOptions)
        }
    }

    internal fun acceptor(): AcceptorOptions {
        return AcceptorOptions(HashMap(customOptions)).apply {
            copyCommon(this@SocketOptions)
        }
    }

    private class GeneralSocketOptions constructor(customOptions: MutableMap<Any, Any?>) :
        SocketOptions(customOptions) {
        override fun copy(): GeneralSocketOptions = GeneralSocketOptions(HashMap(customOptions)).apply {
            copyCommon(this@GeneralSocketOptions)
        }
    }

    /**
     * ToS value, [TypeOfService.UNDEFINED] by default, may not work with old JDK (will be silently ignored)
     */
    var typeOfService: TypeOfService = TypeOfService.UNDEFINED

    /**
     * SO_REUSEADDR option
     */
    var reuseAddress: Boolean = false

    /**
     * SO_REUSEPORT option, may not work with old JDK (will be silently ignored)
     */
    var reusePort: Boolean = false

    /**
     * TCP server socket options
     */
    class AcceptorOptions internal constructor(customOptions: MutableMap<Any, Any?>) : SocketOptions(customOptions) {
        override fun copy(): AcceptorOptions {
            return AcceptorOptions(HashMap(customOptions)).apply {
                copyCommon(this@AcceptorOptions)
            }
        }
    }

    /**
     * Represents TCP client or UDP socket options
     */
    open class PeerSocketOptions internal constructor(customOptions: MutableMap<Any, Any?>) :
        SocketOptions(customOptions) {

        /**
         * Socket ougoing buffer size (SO_SNDBUF), `-1` or `0` to make system decide
         */
        var sendBufferSize: Int = -1

        /**
         * Socket incoming buffer size (SO_RCVBUF), `-1` or `0` to make system decide
         */
        var receiveBufferSize: Int = -1

        @Suppress("KDocMissingDocumentation")
        override fun copyCommon(from: SocketOptions) {
            super.copyCommon(from)
            if (from is PeerSocketOptions) {
                sendBufferSize = from.sendBufferSize
                receiveBufferSize = from.receiveBufferSize
            }
        }

        override fun copy(): PeerSocketOptions {
            return PeerSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@PeerSocketOptions)
            }
        }

        internal fun tcp(): TCPClientSocketOptions {
            return TCPClientSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@PeerSocketOptions)
            }
        }

        internal fun udp(): UDPSocketOptions {
            return UDPSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@PeerSocketOptions)
            }
        }
    }

    /**
     * Represents UDP socket options
     */
    class UDPSocketOptions internal constructor(customOptions: MutableMap<Any, Any?>) :
        PeerSocketOptions(customOptions) {
        override fun copy(): UDPSocketOptions {
            return UDPSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@UDPSocketOptions)
            }
        }
    }

    /**
     * Represents TCP client socket options
     */
    class TCPClientSocketOptions internal constructor(customOptions: MutableMap<Any, Any?>) :
        PeerSocketOptions(customOptions) {
        /**
         * TCP_NODELAY socket option, useful to disable Nagle
         */
        var noDelay: Boolean = true

        /**
         * SO_LINGER option applied at socket close, not recommended to set to 0 however useful for debugging
         * Value of `-1` is the default and means that it is not set and system-dependant
         */
        var lingerSeconds: Int = -1

        /**
         * SO_KEEPALIVE option is to enable/disable TCP keep-alive
         */
        var keepAlive: Boolean? = null

        @Suppress("KDocMissingDocumentation")
        override fun copyCommon(from: SocketOptions) {
            super.copyCommon(from)
            if (from is TCPClientSocketOptions) {
                noDelay = from.noDelay
                lingerSeconds = from.lingerSeconds
                keepAlive = from.keepAlive
            }
        }

        override fun copy(): TCPClientSocketOptions {
            return TCPClientSocketOptions(HashMap(customOptions)).apply {
                copyCommon(this@TCPClientSocketOptions)
            }
        }
    }

    companion object {
        /**
         * Default socket options
         */
        @Deprecated(
            "Not supported anymore", level = DeprecationLevel.ERROR,
            replaceWith = ReplaceWith("TODO(\"Not supported anymore\")")
        )
        val Empty: SocketOptions
            get() = TODO("Not supported anymore")

        internal fun create(): SocketOptions = GeneralSocketOptions(HashMap())
    }
}
