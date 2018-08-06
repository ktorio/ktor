package io.ktor.client.features.cookies

import io.ktor.http.*
import java.util.*
import java.util.concurrent.*

actual class AcceptAllCookiesStorage actual constructor() : CookiesStorage {
    private val data = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override suspend fun get(host: String): Map<String, Cookie>? = data[host]?.let {
        Collections.unmodifiableMap(data[host])
    }

    override suspend fun get(host: String, name: String): Cookie? = data[host]?.get(name)

    override suspend fun addCookie(host: String, cookie: Cookie) {
        init(host)
        data[host]?.set(cookie.name, cookie)
    }

    /**
     * TODO: fix concurrent init
     */
    private fun init(host: String) {
        data.putIfAbsent(host, mutableMapOf())
    }
}
