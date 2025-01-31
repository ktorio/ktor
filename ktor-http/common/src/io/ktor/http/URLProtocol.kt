/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Represents URL protocol
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol)
 *
 * @property name of protocol (schema)
 * @property defaultPort default port for protocol or `-1` if not known
 */
@OptIn(InternalAPI::class)
public data class URLProtocol(val name: String, val defaultPort: Int) : JvmSerializable {
    init {
        require(name.all { it.isLowerCase() }) { "All characters should be lower case" }
    }

    @Suppress("PublicApiImplicitType")
    public companion object {
        /**
         * HTTP with port 80
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol.Companion.HTTP)
         */
        public val HTTP: URLProtocol = URLProtocol("http", 80)

        /**
         * secure HTTPS with port 443
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol.Companion.HTTPS)
         */
        public val HTTPS: URLProtocol = URLProtocol("https", 443)

        /**
         * Web socket over HTTP on port 80
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol.Companion.WS)
         */
        public val WS: URLProtocol = URLProtocol("ws", 80)

        /**
         * Web socket over secure HTTPS on port 443
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol.Companion.WSS)
         */
        public val WSS: URLProtocol = URLProtocol("wss", 443)

        /**
         * Socks proxy url protocol.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol.Companion.SOCKS)
         */
        public val SOCKS: URLProtocol = URLProtocol("socks", 1080)

        /**
         * Protocols by names map
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol.Companion.byName)
         */
        public val byName: Map<String, URLProtocol> = listOf(HTTP, HTTPS, WS, WSS, SOCKS).associateBy { it.name }

        /**
         * Create an instance by [name] or use already existing instance
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLProtocol.Companion.createOrDefault)
         */
        public fun createOrDefault(name: String): URLProtocol = name.toLowerCasePreservingASCIIRules().let {
            byName[it] ?: URLProtocol(it, DEFAULT_PORT)
        }
    }
}

/**
 * Check if the protocol is websocket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.isWebsocket)
 */
public fun URLProtocol.isWebsocket(): Boolean = name == "ws" || name == "wss"

/**
 * Check if the protocol is secure
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.isSecure)
 */
public fun URLProtocol.isSecure(): Boolean = name == "https" || name == "wss"
