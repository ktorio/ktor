package io.ktor.client.features.cookies

import io.ktor.http.*

/**
 * [CookiesStorage] that ignores [addCookie] and returns a list of specified [cookies] when constructed.
 */
class ConstantCookiesStorage(vararg cookies: Cookie) : CookiesStorage {
    private val storage: List<Cookie> = cookies.map { it.fillDefaults(URLBuilder().build()) }.toList()

    override suspend fun get(requestUrl: Url): List<Cookie> = storage.filter { it.matches(requestUrl) }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {}
}
