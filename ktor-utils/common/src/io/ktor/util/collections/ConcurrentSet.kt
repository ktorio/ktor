package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
expect class ConcurrentSet<K>() : MutableSet<K>
