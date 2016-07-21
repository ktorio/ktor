package org.jetbrains.ktor.util

data class URLProtocol(val name: String, val defaultPort: Int) {
    init {
        require(name.all { it.isLowerCase() }) { "All characters should be lower case" }
    }

    @Deprecated("Use name instead as it is always lower case", ReplaceWith("name"))
    val nameLowerCase: String
        get() = name

    companion object {
        val HTTP = URLProtocol("http", 80)
        val HTTPS = URLProtocol("https", 443)
        val WS = URLProtocol("ws", 80)
        val WSS = URLProtocol("wss", 443)

        val byName = listOf(HTTP, HTTPS, WS, WSS).associateBy { it.name }
    }
}