package io.ktor.client.features.cookies

import io.ktor.http.*

/**
 * [CookiesStorage] that ignores [addCookie] and returns a list of specified [cookies] when constructed.
 */
class ConstantCookiesStorage(vararg cookies: Cookie) : CookiesStorage {
    private val storage: Map<String, Cookie> = cookies.map { it.name to it }.toMap()

    override suspend fun get(host: String): Map<String, Cookie>? = storage

    override suspend fun get(host: String, name: String): Cookie? = storage[name]

    override suspend fun addCookie(host: String, cookie: Cookie) {}
}
