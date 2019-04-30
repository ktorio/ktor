/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
actual class ConcurrentMap<Key, Value> : MutableMap<Key, Value> by mutableMapOf() {
    actual fun getOrDefault(key: Key, block: () -> Value): Value {
        get(key)?.let { return it }
        return block().also { put(key, it) }
    }
}
