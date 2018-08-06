package io.ktor.http

/**
 * Represents a value for a `Cache-Control` header
 *
 * @param visibility specifies an optional visibility such as private or public
 */
sealed class CacheControl(val visibility: Visibility?) {

    /**
     * Controls caching by proxies
     */
    enum class Visibility {
        /**
         * Specifies that the response is cacheable by clients and shared (proxy) caches.
         */
        Public,

        /**
         * Specifies that the response is cacheable only on the client and not by shared (proxy server) caches.
         */
        Private
    }

    class NoCache(visibility: Visibility?) : CacheControl(visibility) {
        override fun toString() = if (visibility == null) {
            "no-cache"
        } else {
            "no-cache, ${visibility.name.toLowerCase()}"
        }
    }

    class NoStore(visibility: Visibility?) : CacheControl(visibility) {
        override fun toString() = if (visibility == null) {
            "no-store"
        } else {
            "no-store, ${visibility.name.toLowerCase()}"
        }
    }

    class MaxAge(
        val maxAgeSeconds: Int,
        val proxyMaxAgeSeconds: Int? = null,
        val mustRevalidate: Boolean = false,
        val proxyRevalidate: Boolean = false,
        visibility: Visibility? = null
    ) : CacheControl(visibility) {
        override fun toString(): String {
            val parts = ArrayList<String>(5)
            parts.add("max-age=$maxAgeSeconds")
            if (proxyMaxAgeSeconds != null) {
                parts.add("s-maxage=$proxyMaxAgeSeconds")
            }
            if (mustRevalidate) {
                parts.add("must-revalidate")
            }
            if (proxyRevalidate) {
                parts.add("proxy-revalidate")
            }
            if (visibility != null) {
                parts.add(visibility.name.toLowerCase())
            }

            return parts.joinToString(", ")
        }
    }
}
