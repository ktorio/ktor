package io.ktor.http

import io.ktor.compat.*

data class URLProtocol(val name: String, val defaultPort: Int) {
    init {
        require(name.all { it.isLowerCase() }) { "All characters should be lower case" }
    }

    companion object {
        val HTTP = URLProtocol("http", 80)
        val HTTPS = URLProtocol("https", 443)
        val WS = URLProtocol("ws", 80)
        val WSS = URLProtocol("wss", 443)

        val byName = listOf(HTTP, HTTPS, WS, WSS).associateBy { it.name }

        fun createOrDefault(name: String): URLProtocol = name.toLowerCase().let {
            byName[it] ?: URLProtocol(it, -1)
        }
    }
}

fun URLProtocol.isWebsocket(): Boolean =
    (name.equals("ws", ignoreCase = true) || name.equals("wss", ignoreCase = true))

fun URLProtocol.isSecure(): Boolean =
    (name.equals("https", ignoreCase = true) || name.equals("wss", ignoreCase = true))
