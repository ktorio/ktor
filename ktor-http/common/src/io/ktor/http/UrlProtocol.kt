/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/**
 * Typealias for backwards compatibility.
 */
@Deprecated("Use UrlProtocol", replaceWith = ReplaceWith("UrlProtocol"), level = DeprecationLevel.WARNING)
public typealias URLProtocol = UrlProtocol

/**
 * Represents URL protocol
 * @property name of protocol (schema)
 * @property defaultPort default port for protocol or `-1` if not known
 */
public data class UrlProtocol(val name: String, val defaultPort: Int) {
    init {
        require(name.all { it.isLowerCase() }) { "All characters should be lower case" }
    }

    @Suppress("PublicApiImplicitType")
    public companion object {
        /**
         * HTTP with port 80
         */
        public val HTTP: UrlProtocol = UrlProtocol("http", 80)

        /**
         * secure HTTPS with port 443
         */
        public val HTTPS: UrlProtocol = UrlProtocol("https", 443)

        /**
         * Web socket over HTTP on port 80
         */
        public val WS: UrlProtocol = UrlProtocol("ws", 80)

        /**
         * Web socket over secure HTTPS on port 443
         */
        public val WSS: UrlProtocol = UrlProtocol("wss", 443)

        /**
         * Socks proxy url protocol.
         */
        public val SOCKS: UrlProtocol = UrlProtocol("socks", 1080)

        /**
         * Protocols by names map
         */
        public val byName: Map<String, UrlProtocol> = listOf(HTTP, HTTPS, WS, WSS, SOCKS).associateBy { it.name }

        /**
         * Create an instance by [name] or use already existing instance
         */
        public fun createOrDefault(name: String): UrlProtocol = name.toLowerCasePreservingASCIIRules().let {
            byName[it] ?: UrlProtocol(it, DEFAULT_PORT)
        }
    }

    override fun toString(): String = name
}

/**
 * Placeholder for indicating when a URI includes the //.
 */
public object ProtocolSeparator

/**
 * Check if the protocol is websocket
 */
public fun UrlProtocol.isWebsocket(): Boolean = name == "ws" || name == "wss"

/**
 * Check if the protocol is secure
 */
public fun UrlProtocol.isSecure(): Boolean = name == "https" || name == "wss"
