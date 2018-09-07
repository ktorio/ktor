package io.ktor.http

import io.ktor.util.*

/**
 * Represents URL protocol
 * @property name of protocol (schema)
 * @property defaultPort default port for protocol or `-1` if not known
 */
data class URLProtocol(val name: String, val defaultPort: Int) {
    init {
        require(name.all { it.isLowerCase() }) { "All characters should be lower case" }
    }

    @Suppress("PublicApiImplicitType")
    companion object {
        /**
         * HTTP with port 80
         */
        val HTTP = URLProtocol("http", 80)

        /**
         * secure HTTPS with port 443
         */
        val HTTPS = URLProtocol("https", 443)

        /**
         * Web socket over HTTP on port 80
         */
        val WS = URLProtocol("ws", 80)

        /**
         * Web socket over secure HTTPS on port 443
         */
        val WSS = URLProtocol("wss", 443)

        /**
         * Protocols by names map
         */
        val byName: Map<String, URLProtocol> = listOf(HTTP, HTTPS, WS, WSS).associateBy { it.name }

        /**
         * Create an instance by [name] or use already existing instance
         */
        fun createOrDefault(name: String): URLProtocol = name.toLowerCase().let {
            byName[it] ?: URLProtocol(it, DEFAULT_PORT)
        }
    }
}

/**
 * Check if the protocol is websocket
 */
fun URLProtocol.isWebsocket(): Boolean = name == "ws" || name == "wss"

/**
 * Check if the protocol is secure
 */
fun URLProtocol.isSecure(): Boolean = name == "https" || name == "wss"
