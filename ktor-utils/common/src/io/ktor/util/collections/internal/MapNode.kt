/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*

internal class MapNode<Key, Value>(override val key: Key, value: Value) : MutableMap.MutableEntry<Key, Value> {
    internal var backReference: ForwardListNode<MapNode<Key, Value>>? by shared(null)
    override var value: Value by shared(value)

    val hash: Int = key.hashCode()

    init {
        makeShared()
    }

    override fun setValue(newValue: Value): Value {
        val result = value
        value = newValue
        return result
    }

    internal fun remove() {
        backReference!!.remove()
        backReference = null
    }

    override fun toString(): String = "MapItem[$key, $value]"
}
