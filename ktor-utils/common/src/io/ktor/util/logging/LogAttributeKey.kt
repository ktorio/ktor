/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.util.*

/**
 * Log event custom field descriptor
 * @property name of the key, useful for debugging. Could be unique but it is not required.
 * @property initial value assigned to the field
 */
@KtorExperimentalAPI
abstract class LogAttributeKey<T>(val name: String, val initial: T) {
    private var indexField: Int = -1

    /**
     * Check if registered in a [Config].
     */
    val registered: Boolean get() = indexField != -1

    internal var index: Int
        get() = when (indexField) {
            -1 -> error("Key is not registered in the config yet")
            else -> indexField
        }
        set(newIndex) {
            require(newIndex >= 0) { "index shouldn't be negative: $newIndex" }
            check(!registered) { "Key is already registered" }
            indexField = newIndex
        }

    override fun toString(): String {
        return "LogAttributeKey{name=$name}"
    }
}
