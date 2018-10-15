package io.ktor.client.features.cookies

import io.ktor.http.*

/**
 * Storage for [Cookie].
 */
interface CookiesStorage {
    /**
     * Gets a map of [String] to [Cookie] for a specific [host].
     */
    suspend fun get(requestUrl: Url): List<Cookie>

    /**
     * Sets a [cookie] for the specified [host].
     */
    suspend fun addCookie(requestUrl: Url, cookie: Cookie)
}

suspend fun CookiesStorage.addCookie(urlString: String, cookie: Cookie) {
    addCookie(Url(urlString), cookie)
}

internal fun Cookie.matches(requestUrl: Url): Boolean {
    val domain = domain?.toLowerCase()?.trimStart('.') ?: error("Domain field should have the default value")
    val path = with(path) {
        val current = path ?: error("Path field should have the default value")
        if (current.endsWith('/')) current else "$path/"
    }

    val host = requestUrl.host.toLowerCase()
    val requestPath = let {
        val pathInRequest = requestUrl.encodedPath
        if (pathInRequest.endsWith('/')) pathInRequest else "$pathInRequest/"
    }

    if (host != domain && (hostIsIp(host) || !host.endsWith(".$domain"))) return false

    if (path != "/" &&
        requestPath != path &&
        !requestPath.startsWith(path)
    ) return false

    if (secure && !requestUrl.protocol.isSecure()) return false
    return true
}

internal fun Cookie.fillDefaults(requestUrl: Url): Cookie {
    var result = this

    if (result.path?.startsWith("/") != true) {
        result = result.copy(path = requestUrl.encodedPath)
    }

    if (result.domain.isNullOrBlank()) {
        result = result.copy(domain = requestUrl.host)
    }

    return result
}
