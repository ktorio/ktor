/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.utils.io.*
import kotlin.jvm.JvmField

/**
 * Represents an HTTP method (verb)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpMethod)
 *
 * @property value contains method name
 */
public data class HttpMethod(val value: String) {
    override fun toString(): String = value

    // TODO: remove deprecated getters in the next major release.
    @Suppress("KDocMissingDocumentation")
    public companion object {
        @JvmField
        public val Get: HttpMethod = HttpMethod("GET")

        @JvmField
        public val Post: HttpMethod = HttpMethod("POST")

        @JvmField
        public val Put: HttpMethod = HttpMethod("PUT")

        // https://tools.ietf.org/html/rfc5789
        @JvmField
        public val Patch: HttpMethod = HttpMethod("PATCH")

        @JvmField
        public val Delete: HttpMethod = HttpMethod("DELETE")

        @JvmField
        public val Head: HttpMethod = HttpMethod("HEAD")

        @JvmField
        public val Options: HttpMethod = HttpMethod("OPTIONS")

        @JvmField
        public val Trace: HttpMethod = HttpMethod("TRACE")

        @JvmField
        public val Query: HttpMethod = HttpMethod("QUERY")

        /**
         * Parse HTTP method by [method] string
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpMethod.Companion.parse)
         */
        public fun parse(method: String): HttpMethod {
            return when (method) {
                Get.value -> Get
                Post.value -> Post
                Put.value -> Put
                Patch.value -> Patch
                Trace.value -> Trace
                Delete.value -> Delete
                Head.value -> Head
                Query.value -> Query
                Options.value -> Options
                else -> HttpMethod(method)
            }
        }

        /**
         * A list of default HTTP methods
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpMethod.Companion.DefaultMethods)
         */
        @JvmField
        public val DefaultMethods: List<HttpMethod> = listOf(Get, Post, Put, Patch, Delete, Head, Options)

        @Deprecated("Use Get const instead", ReplaceWith("HttpMethod.Get"))
        public fun getGet(): HttpMethod = Get

        @Deprecated("Use Post const instead", ReplaceWith("HttpMethod.Post"))
        public fun getPost(): HttpMethod = Post

        @Deprecated("Use Put const instead", ReplaceWith("HttpMethod.Put"))
        public fun getPut(): HttpMethod = Put

        @Deprecated("Use Patch const instead", ReplaceWith("HttpMethod.Patch"))
        public fun getPatch(): HttpMethod = Patch

        @Deprecated("Use Delete const instead", ReplaceWith("HttpMethod.Delete"))
        public fun getDelete(): HttpMethod = Delete

        @Deprecated("Use Head const instead", ReplaceWith("HttpMethod.Head"))
        public fun getHead(): HttpMethod = Head

        @Deprecated("Use Options const instead", ReplaceWith("HttpMethod.Options"))
        public fun getOptions(): HttpMethod = Options

        @Deprecated("Use Trace const instead", ReplaceWith("HttpMethod.Trace"))
        public fun getTrace(): HttpMethod = Trace

        @Deprecated("Use DefaultMethods const instead", ReplaceWith("HttpMethod.DefaultMethods"))
        public fun getDefaultMethods(): List<HttpMethod> = DefaultMethods
    }
}

private val REQUESTS_WITHOUT_BODY = setOf(
    HttpMethod.Get,
    HttpMethod.Head,
    HttpMethod.Options,
    HttpMethod.Trace,
)

/**
 * Returns `true` if this request method can have a request body.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.supportsRequestBody)
 */
@InternalAPI
public val HttpMethod.supportsRequestBody: Boolean
    get() = this !in REQUESTS_WITHOUT_BODY
