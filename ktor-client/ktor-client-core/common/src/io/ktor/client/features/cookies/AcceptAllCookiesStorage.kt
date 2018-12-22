package io.ktor.client.features.cookies

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlin.math.*

/**
 * [CookiesStorage] that stores all the cookies in an in-memory map.
 */
class AcceptAllCookiesStorage() : CookiesStorage {
    private val container: MutableList<Cookie> = mutableListOf()
    private val oldestCookie: AtomicLong = atomic(0L)
    private val mutex = Lock()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.use {
        val date = GMTDate()
        if (date.timestamp < oldestCookie.value) cleanup(date.timestamp)

        return container.filter { it.matches(requestUrl) }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.use {
        with(cookie) {
            if (name.isBlank()) return@use
        }

        container.removeAll { it.name == cookie.name && it.matches(requestUrl) }
        container.add(cookie.fillDefaults(requestUrl))
    }

    private fun cleanup(timestamp: Long) {
        container.removeAll { cookie ->
            val expires = cookie.expires?.timestamp ?: return@removeAll false
            expires < timestamp
        }

        val newOldest = container.fold(Long.MAX_VALUE) { acc, cookie ->
            cookie.expires?.timestamp?.let { min(acc, it) } ?: acc
        }

        oldestCookie.value = newOldest
    }
}
