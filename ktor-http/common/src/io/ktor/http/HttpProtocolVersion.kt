/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

// ABOUTME: Represents HTTP protocol version (e.g., HTTP/1.1, HTTP/2.0).
// ABOUTME: Provides cached instances for common versions and zero-allocation parsing for HTTP protocol.

package io.ktor.http

/**
 * Represents an HTTP protocol version.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion)
 *
 * @property name specifies name of the protocol, e.g. "HTTP".
 * @property major specifies protocol major version.
 * @property minor specifies protocol minor version.
 */
public data class HttpProtocolVersion(val name: String, val major: Int, val minor: Int) {
    @Suppress("PublicApiImplicitType")
    public companion object {
        /**
         * HTTP/3.0 version.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.HTTP_3_0)
         */
        public val HTTP_3_0: HttpProtocolVersion = HttpProtocolVersion("HTTP", 3, 0)

        /**
         * HTTP/2.0 version.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.HTTP_2_0)
         */
        public val HTTP_2_0: HttpProtocolVersion = HttpProtocolVersion("HTTP", 2, 0)

        /**
         * HTTP/1.1 version.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.HTTP_1_1)
         */
        public val HTTP_1_1: HttpProtocolVersion = HttpProtocolVersion("HTTP", 1, 1)

        /**
         * HTTP/1.0 version.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.HTTP_1_0)
         */
        public val HTTP_1_0: HttpProtocolVersion = HttpProtocolVersion("HTTP", 1, 0)

        /**
         * SPDY/3.0 version.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.SPDY_3)
         */
        public val SPDY_3: HttpProtocolVersion = HttpProtocolVersion("SPDY", 3, 0)

        /**
         * QUIC/1.0 version.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.QUIC)
         */
        public val QUIC: HttpProtocolVersion = HttpProtocolVersion("QUIC", 1, 0)

        /**
         * Creates an instance of [HttpProtocolVersion] from the given parameters.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.fromValue)
         */
        public fun fromValue(name: String, major: Int, minor: Int): HttpProtocolVersion = when {
            name == "HTTP" && major == 1 && minor == 0 -> HTTP_1_0
            name == "HTTP" && major == 1 && minor == 1 -> HTTP_1_1
            name == "HTTP" && major == 2 && minor == 0 -> HTTP_2_0
            name == "HTTP" && major == 3 && minor == 0 -> HTTP_3_0
            else -> HttpProtocolVersion(name, major, minor)
        }

        /**
         * Create an instance of [HttpProtocolVersion] from http string representation.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpProtocolVersion.Companion.parse)
         */
        public fun parse(value: CharSequence): HttpProtocolVersion {
            // Format: protocol/major.minor
            // Zero-allocation fast path for common HTTP versions
            val slashIndex = value.indexOf('/')
            val dotIndex = if (slashIndex >= 0) value.indexOf('.', slashIndex + 1) else -1

            if (slashIndex < 0 || dotIndex < 0) {
                throw IllegalArgumentException(
                    "Failed to parse HttpProtocolVersion. Expected format: protocol/major.minor, but actual: $value"
                )
            }

            val major = parseDigits(value, slashIndex + 1, dotIndex)
            val minor = parseDigits(value, dotIndex + 1, value.length)

            if (major < 0 || minor < 0) {
                throw IllegalArgumentException(
                    "Failed to parse HttpProtocolVersion. Expected format: protocol/major.minor, but actual: $value"
                )
            }

            // Fast path for HTTP (most common) - returns cached instances, no allocation
            if (isHttp(value, slashIndex)) {
                return when {
                    major == 1 && minor == 0 -> HTTP_1_0
                    major == 1 && minor == 1 -> HTTP_1_1
                    major == 2 && minor == 0 -> HTTP_2_0
                    major == 3 && minor == 0 -> HTTP_3_0
                    else -> HttpProtocolVersion("HTTP", major, minor)
                }
            }

            // Slow path for other protocols - needs string allocation
            val protocol = value.substring(0, slashIndex)
            return fromValue(protocol, major, minor)
        }

        private fun isHttp(value: CharSequence, slashIndex: Int): Boolean {
            return slashIndex == 4 &&
                value[0] == 'H' &&
                value[1] == 'T' &&
                value[2] == 'T' &&
                value[3] == 'P'
        }

        private fun parseDigits(value: CharSequence, start: Int, end: Int): Int {
            if (start >= end) return -1
            var result = 0
            for (i in start until end) {
                val c = value[i]
                if (c !in '0'..'9') return -1
                result = result * 10 + (c - '0')
            }
            return result
        }
    }

    override fun toString(): String = "$name/$major.$minor"
}
