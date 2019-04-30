/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
expect class ConcurrentMap<Key, Value>() : MutableMap<Key, Value> {
    fun getOrDefault(key: Key, block: () -> Value): Value
}
