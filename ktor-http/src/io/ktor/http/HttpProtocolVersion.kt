package io.ktor.http


data class HttpProtocolVersion(val name: String, val major: Int, val minor: Int) {
    companion object {
        val HTTP_2_0 = HttpProtocolVersion("HTTP", 2, 0)
        val HTTP_1_1 = HttpProtocolVersion("HTTP", 1, 1)
        val HTTP_1_0 = HttpProtocolVersion("HTTP", 1, 0)

        val SPDY_3 = HttpProtocolVersion("SPDY", 3, 0)
        val QUIC = HttpProtocolVersion("QUIC", 1, 0)

        fun fromValue(name: String, major: Int, minor: Int): HttpProtocolVersion = when {
            name == "HTTP" && major == 1 && minor == 1 -> HTTP_1_1
            name == "HTTP" && major == 2 && minor == 0 -> HTTP_2_0
            else -> HttpProtocolVersion(name, major, minor)
        }
    }

    override fun toString(): String = "$name/$major.$minor"
}
