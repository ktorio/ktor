package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
actual class ConcurrentSet<K> actual constructor() : MutableSet<K> by mutableSetOf()
