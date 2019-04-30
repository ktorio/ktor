/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
actual class ConcurrentSet<K> actual constructor() : MutableSet<K> by mutableSetOf()
