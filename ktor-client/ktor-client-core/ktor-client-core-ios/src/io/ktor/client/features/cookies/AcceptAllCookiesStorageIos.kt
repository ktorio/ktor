package io.ktor.client.features.cookies

import io.ktor.http.*
import konan.worker.*

actual class AcceptAllCookiesStorage actual constructor() : CookiesStorage {
    private val data = mutableMapOf<String, MutableMap<String, Cookie>>()

    override suspend fun get(host: String): Map<String, Cookie>? = data[host]?.let { data[host].deepCopy() }

    override suspend fun get(host: String, name: String): Cookie? = data[host]?.get(name)

    override suspend fun addCookie(host: String, cookie: Cookie) {
        init(host)
        data[host]?.set(cookie.name, cookie)
    }

    private fun init(host: String) {
        if (!data.containsKey(host)) data[host] = mutableMapOf()
    }
}
