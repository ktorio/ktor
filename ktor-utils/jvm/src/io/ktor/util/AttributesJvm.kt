/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import java.util.concurrent.*

/**
 * Create JVM specific attributes instance.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.Attributes)
 */
public actual fun Attributes(concurrent: Boolean): Attributes =
    if (concurrent) ConcurrentSafeAttributes() else HashMapAttributes()

private class ConcurrentSafeAttributes : BaseAttributes() {
    override val map: ConcurrentHashMap<AttributeKey<*>, Any> = ConcurrentHashMap()

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value.
     * Note: [block] could be eventually evaluated twice for the same key.
     * TODO: To be discussed. Workaround for android < API 24.
     */
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        map[key]?.let { return it as T }
        val result = block()
        @Suppress("UNCHECKED_CAST")
        return (map.putIfAbsent(key, result) ?: result) as T
    }
}
