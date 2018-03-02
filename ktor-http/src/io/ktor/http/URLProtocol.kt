package io.ktor.http

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