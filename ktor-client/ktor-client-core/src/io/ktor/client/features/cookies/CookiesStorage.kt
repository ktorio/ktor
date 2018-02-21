package io.ktor.client.features.cookies

import io.ktor.http.*
import java.util.*
import java.util.concurrent.*


interface CookiesStorage {
    suspend fun get(host: String): Map<String, Cookie>?
    suspend fun get(host: String, name: String): Cookie?
    suspend fun addCookie(host: String, cookie: Cookie)

}

suspend inline fun CookiesStorage.forEach(host: String, block: (Cookie) -> Unit) {
    get(host)?.forEach { block(it.value) }
}

open class AcceptAllCookiesStorage : CookiesStorage {
    private val data = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override suspend fun get(host: String): Map<String, Cookie>? = data[host]?.let {
        Collections.unmodifiableMap(data[host])
    }

    override suspend fun get(host: String, name: String): Cookie? = data[host]?.get(name)

    override suspend fun addCookie(host: String, cookie: Cookie) {
        init(host)
        data[host]?.set(cookie.name, cookie)
    }

    private fun init(host: String) {
        if (!data.containsKey(host)) {
            data[host] = mutableMapOf()
        }
    }
}

class ConstantCookieStorage(vararg cookies: Cookie) : CookiesStorage {
    private val storage: Map<String, Cookie> = cookies.map { it.name to it }.toMap()

    override suspend fun get(host: String): Map<String, Cookie>? = storage

    override suspend fun get(host: String, name: String): Cookie? = storage[name]

    override suspend fun addCookie(host: String, cookie: Cookie) {}
}
