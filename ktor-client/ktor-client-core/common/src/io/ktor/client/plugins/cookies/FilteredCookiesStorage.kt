/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.http.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.sync.*
import kotlin.math.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Decides whether a cookie received from a request URL should be stored.
 *
 * Policies are evaluated by [FilteredCookiesStorage] in the order in which they were supplied.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.CookieAcceptancePolicy)
 */
public fun interface CookieAcceptancePolicy {
    /**
     * Returns `true` when [cookie] received from [requestUrl] should be stored.
     *
     * @param requestUrl the URL that returned the cookie.
     * @param cookie the cookie to evaluate.
     * @return `true` to store the cookie or `false` to ignore it.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.CookieAcceptancePolicy.shouldAccept)
     */
    public suspend fun shouldAccept(requestUrl: Url, cookie: Cookie): Boolean
}

/**
 * An in-memory [CookiesStorage] that stores cookies accepted by all [policies].
 *
 * Mandatory cookie name and domain validation is always applied before the policies.
 *
 * @param policies cookie acceptance policies, evaluated in the supplied order.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.FilteredCookiesStorage)
 */
@OptIn(ExperimentalTime::class)
public class FilteredCookiesStorage internal constructor(
    private val policies: List<CookieAcceptancePolicy>,
    private val clock: () -> Long
) : CookiesStorage {

    /**
     * Creates an initially empty in-memory cookie storage filtered by [policies].
     *
     * @param policies cookie acceptance policies, evaluated in the supplied order.
     */
    public constructor(
        vararg policies: CookieAcceptancePolicy,
        clock: Clock = Clock.System
    ) : this(policies = policies.toList(), clock = { clock.now().toEpochMilliseconds() })

    private data class CookieWithTimestamp(val cookie: Cookie, val createdAt: Long)

    private val container: MutableList<CookieWithTimestamp> = mutableListOf()
    private val oldestCookie: AtomicLong = atomic(0L)
    private val mutex = Mutex()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val now = clock()
        if (now >= oldestCookie.value) cleanup(now)

        val cookies = container.filter { it.cookie.matches(requestUrl) }.map { it.cookie }
        return@withLock cookies
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val cookieDomain = cookie.domain
        with(cookie) {
            if (name.isBlank()) return
            if (!cookieDomain.isNullOrBlank() && !requestUrl.host.matchesDomain(cookieDomain)) return
        }

        for (policy in policies) {
            if (!policy.shouldAccept(requestUrl, cookie)) return
        }

        mutex.withLock {
            container.removeAll { (existingCookie, _) ->
                existingCookie.name == cookie.name && existingCookie.matches(requestUrl)
            }
            val createdAt = clock()
            container.add(CookieWithTimestamp(cookie.fillDefaults(requestUrl), createdAt))

            cookie.maxAgeOrExpires(createdAt)?.let {
                if (oldestCookie.value > it) {
                    oldestCookie.value = it
                }
            }
        }
    }

    override fun close() {}

    private fun cleanup(timestamp: Long) {
        container.removeAll { (cookie, createdAt) ->
            val expires = cookie.maxAgeOrExpires(createdAt) ?: return@removeAll false
            expires < timestamp
        }

        val newOldest = container.fold(Long.MAX_VALUE) { acc, (cookie, createdAt) ->
            cookie.maxAgeOrExpires(createdAt)?.let { min(acc, it) } ?: acc
        }

        oldestCookie.value = newOldest
    }

    private fun Cookie.maxAgeOrExpires(createdAt: Long): Long? =
        maxAge?.let { createdAt + it * 1000L } ?: expires?.timestamp
}
