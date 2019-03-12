package io.ktor.util.collections

import io.ktor.util.*
import java.util.*

@InternalAPI
actual class ConcurrentSet<K> actual constructor() : MutableSet<K> by Collections.synchronizedSet(mutableSetOf())
