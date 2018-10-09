package io.ktor.http


/**
 * Represents an HTTP protocol version.
 * @property name specifies name of the protocol, e.g. "HTTP".
 * @property major specifies protocol major version.
 * @property minor specifies protocol minor version.
 */
data class HttpProtocolVersion(val name: String, val major: Int, val minor: Int) {
    @Suppress("PublicApiImplicitType")
    companion object {
        /**
         * HTTP/2.0 version.
         */
        val HTTP_2_0 = HttpProtocolVersion("HTTP", 2, 0)
        
        /**
         * HTTP/1.1 version.
         */
        val HTTP_1_1 = HttpProtocolVersion("HTTP", 1, 1)
        
        /**
         * HTTP/1.0 version.
         */
        val HTTP_1_0 = HttpProtocolVersion("HTTP", 1, 0)

        /**
         * SPDY/3.0 version.
         */
        val SPDY_3 = HttpProtocolVersion("SPDY", 3, 0)

        /**
         * QUIC/1.0 version.
         */
        val QUIC = HttpProtocolVersion("QUIC", 1, 0)

        /**
         * Creates an instance of [HttpProtocolVersion] from the given parameters. 
         */
        fun fromValue(name: String, major: Int, minor: Int): HttpProtocolVersion = when {
            name == "HTTP" && major == 1 && minor == 1 -> HTTP_1_1
            name == "HTTP" && major == 2 && minor == 0 -> HTTP_2_0
            else -> HttpProtocolVersion(name, major, minor)
        }
    }

    override fun toString(): String = "$name/$major.$minor"
}
