package io.ktor.client.features.cookies

import io.ktor.http.*
import java.util.*
import java.util.concurrent.*

/**
 * Storage for [Cookie].
 */
interface CookiesStorage {
    /**
     * Gets a map of [String] to [Cookie] for a specific [host].
     */
    suspend fun get(host: String): Map<String, Cookie>?

    /**
     * Try to get a [Cookie] with the specified cookie's [name] for a [host].
     */
    suspend fun get(host: String, name: String): Cookie?

    /**
     * Sets a [cookie] for the specified [host].
     */
    suspend fun addCookie(host: String, cookie: Cookie)
}

/**
 * Runs a [block] of code, for all the cookies set in the specified [host].
 */
suspend inline fun CookiesStorage.forEach(host: String, block: (Cookie) -> Unit) {
    get(host)?.forEach { block(it.value) }
}

/**
 * [CookiesStorage] that stores all the cookies in an in-memory map.
 */
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

/**
 * [CookiesStorage] that ignores [addCookie] and returns a list of specified [cookies] when constructed.
 */
class ConstantCookieStorage(vararg cookies: Cookie) : CookiesStorage {
    private val storage: Map<String, Cookie> = cookies.map { it.name to it }.toMap()

    override suspend fun get(host: String): Map<String, Cookie>? = storage

    override suspend fun get(host: String, name: String): Cookie? = storage[name]

    override suspend fun addCookie(host: String, cookie: Cookie) {}
}
