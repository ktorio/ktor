/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.cookies

import io.ktor.client.utils.*
import io.ktor.client.utils.sharedList
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.util.withLock
import kotlinx.atomicfu.*
import kotlinx.coroutines.sync.*
import kotlin.math.*

/**
 * [CookiesStorage] that stores all the cookies in an in-memory map.
 */
public class AcceptAllCookiesStorage : CookiesStorage {
    private val container: MutableList<Cookie> = sharedList()
    private val oldestCookie: AtomicLong = atomic(0L)
    private val mutex = Mutex()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val date = GMTDate()
        if (date.timestamp >= oldestCookie.value) cleanup(date.timestamp)

        return@withLock container.filter { it.matches(requestUrl) }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.withLock {
        with(cookie) {
            if (name.isBlank()) return@withLock
        }

        container.removeAll { it.name == cookie.name && it.matches(requestUrl) }
        container.add(cookie.fillDefaults(requestUrl))
        cookie.expires?.timestamp?.let { expires ->
            if (oldestCookie.value > expires) {
                oldestCookie.value = expires
            }
        }
    }

    override fun close() {
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
