package io.ktor.http

/**
 * Represents an HTTP method (verb)
 * @property value contains method name
 */
data class HttpMethod(val value: String) {
    @Suppress("KDocMissingDocumentation", "PublicApiImplicitType")
    companion object {
        val Get = HttpMethod("GET")
        val Post = HttpMethod("POST")
        val Put = HttpMethod("PUT")
        val Patch = HttpMethod("PATCH") // https://tools.ietf.org/html/rfc5789
        val Delete = HttpMethod("DELETE")
        val Head = HttpMethod("HEAD")
        val Options = HttpMethod("OPTIONS")

        /**
         * Parse HTTP method by [method] string
         */
        fun parse(method: String): HttpMethod {
            return when (method) {
                Get.value -> Get
                Post.value -> Post
                Put.value -> Put
                Patch.value -> Patch
                Delete.value -> Delete
                Head.value -> Head
                Options.value -> Options
                else -> HttpMethod(method)
            }
        }

        /**
         * A list of default HTTP methods
         */
        val DefaultMethods: List<HttpMethod> = listOf(Get, Post, Put, Patch, Delete, Head, Options)
    }
}
