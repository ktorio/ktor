package io.ktor.client.utils

import io.ktor.http.*


object CacheControl {
    val MAX_AGE = "max-age"
    val MIN_FRESH = "min-fresh"
    val ONLY_IF_CACHED = "only-if-cached"

    val MAX_STALE = "max-stale"
    val NO_CACHE = "no-cache"
    val NO_STORE = "no-store"
    val NO_TRANSFORM = "no-transform"

    val MUST_REVALIDATE = "must-revalidate"
    val PUBLIC = "private"
    val PRIVATE = "private"
    val PROXY_REVALIDATE = "proxy-revalidate"
    val S_MAX_AGE = "s-maxage"
}

fun Headers.cacheControl(): List<String> = getAll(HttpHeaders.CacheControl) ?: listOf()
fun Headers.maxAge(): Int? = cacheControl(CacheControl.MAX_AGE)
fun Headers.onlyIfCached(): Boolean = cacheControl(CacheControl.ONLY_IF_CACHED) ?: false

fun HeadersBuilder.maxAge(value: Int) = append(HttpHeaders.CacheControl, "${CacheControl.MAX_AGE}=$value")

inline fun <reified T> Headers.cacheControl(key: String): T? = when (T::class) {
    Int::class -> cacheControl().intProperty(key) as T?
    Boolean::class -> cacheControl().booleanProperty(key) as T?
    else -> null
}

fun List<String>.booleanProperty(key: String): Boolean = contains(key)

fun List<String>.intProperty(key: String): Int? =
        find { it.startsWith(key) }?.split("=")?.getOrNull(1)?.toInt()
