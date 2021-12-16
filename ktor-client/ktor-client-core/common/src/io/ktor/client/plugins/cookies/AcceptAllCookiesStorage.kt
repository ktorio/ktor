/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cookies

import io.ktor.http.*
import io.ktor.util.collections.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.sync.*
import kotlinx.datetime.*
import kotlin.math.*

/**
 * [CookiesStorage] that stores all the cookies in an in-memory map.
 */
@Suppress("DEPRECATION")
public class AcceptAllCookiesStorage : CookiesStorage {
    private val container: MutableList<Cookie> = sharedList()
    private val oldestCookie = atomic(Instant.DISTANT_PAST)
    private val mutex = Mutex()

    override suspend fun get(requestUrl: Url, now: Instant): List<Cookie> = mutex.withLock {
        if (now >= oldestCookie.value) cleanup(now)

        return@withLock container.filter { it.matches(requestUrl) }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.withLock {
        with(cookie) {
            if (name.isBlank()) return@withLock
        }

        container.removeAll { it.name == cookie.name && it.matches(requestUrl) }
        container.add(cookie.fillDefaults(requestUrl))
        cookie.expires?.let { expires ->
            if (oldestCookie.value > expires) {
                oldestCookie.value = expires
            }
        }
    }

    override fun close() {
    }

    private fun cleanup(timestamp: Instant) {
        container.removeAll { cookie ->
            val expires = cookie.expires ?: return@removeAll false
            expires < timestamp
        }

        val newOldest = container.minOf { it.expires ?: Instant.DISTANT_PAST }

        oldestCookie.value = newOldest
    }
}
