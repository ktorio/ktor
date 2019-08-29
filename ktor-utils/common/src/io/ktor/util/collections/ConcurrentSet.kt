/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
class ConcurrentSet<K> constructor(
    private val delegate: MutableSet<K> = mutableSetOf(),
    private val lock: Lock = Lock()
) : ConcurrentCollection<K>(delegate, lock), MutableSet<K>
