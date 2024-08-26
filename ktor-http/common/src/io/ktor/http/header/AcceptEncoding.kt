/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.header

import io.ktor.http.*

/**
 * Represents the `Accept-Encoding` HTTP header, which specifies the content encoding the client is willing to accept.
 *
 * @property acceptEncoding The encoding type as a string, such as "gzip", "compress", or "br".
 * @param parameters Optional list of parameters associated with the encoding, such as quality values (q-values).
 */
public class AcceptEncoding(
    public val acceptEncoding: String,
    parameters: List<HeaderValueParam> = emptyList()
) : HeaderValueWithParameters(acceptEncoding, parameters) {

    /**
     * Companion object containing predefined commonly used `Accept-Encoding` values.
     */
    public companion object {
        public val gzip: AcceptEncoding = AcceptEncoding("gzip")
        public val compress: AcceptEncoding = AcceptEncoding("compress")
        public val deflate: AcceptEncoding = AcceptEncoding("deflate")
        public val br: AcceptEncoding = AcceptEncoding("br")
        public val zstd: AcceptEncoding = AcceptEncoding("zstd")
        public val identity: AcceptEncoding = AcceptEncoding("identity")
        public val all: AcceptEncoding = AcceptEncoding("*")

        /**
         * Merges multiple `AcceptEncoding` instances into a single string separated by commas.
         *
         * @param encodings A variable number of `AcceptEncoding` objects to be merged.
         * @return A string representing the merged `Accept-Encoding` values.
         */
        public fun mergeAcceptEncodings(vararg encodings: AcceptEncoding): String {
            return encodings.joinToString(separator = ", ")
        }
    }

    /**
     * Returns a new `AcceptEncoding` instance with the specified q-value parameter.
     *
     * @param qValue The q-value to be associated with this encoding. The value should be between 0.0 and 1.0.
     * @return A new `AcceptEncoding` instance with the specified q-value, or the same instance if the q-value is already set.
     */
    public fun withQValue(qValue: Double): AcceptEncoding {
        if (qValue.toString() == parameter("q")) {
            return this
        }

        return AcceptEncoding(acceptEncoding, listOf(HeaderValueParam("q", qValue.toString())))
    }

    override fun equals(other: Any?): Boolean = other is AcceptEncoding
            && acceptEncoding.equals(other.acceptEncoding, ignoreCase = true)
            && parameters == other.parameters

    override fun hashCode(): Int {
        var hashCode = acceptEncoding.lowercase().hashCode()
        hashCode += 31 * parameters.hashCode()
        return hashCode
    }

}
