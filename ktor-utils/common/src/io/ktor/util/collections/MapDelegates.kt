/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import io.ktor.utils.io.*
import kotlin.reflect.KProperty

@InternalAPI
public interface StringMap {
    public operator fun set(key: String, value: String)
    public operator fun get(key: String): String?
    public fun remove(key: String): String?
}

@InternalAPI
public interface StringMapDelegate : StringMap {
    public val map: MutableMap<String, String>

    override fun set(key: String, value: String): Unit = map.set(key, value)
    override fun get(key: String): String? = map[key]
    override fun remove(key: String): String? = map.remove(key)
}

/**
 * Simplifies property access delegation for string maps when using a string constant.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.getValue)
 */
@InternalAPI
public operator fun String.getValue(thisRef: StringMap, property: KProperty<*>): String? =
    thisRef[this]

/**
 * Simplifies property assignment delegation for string maps when using a string constant.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.setValue)
 */
@InternalAPI
public operator fun String.setValue(thisRef: StringMap, property: KProperty<*>, value: String?) {
    if (value == null) {
        thisRef.remove(this)
    } else {
        thisRef[this] = value
    }
}

/**
 * Simplifies property access delegation for HxAttributes when setting attribute values from a string constant.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.getValue)
 */
@InternalAPI
public operator fun <T> SerializedMapValue<T>.getValue(thisRef: StringMap, property: KProperty<*>): T? =
    thisRef[key]?.let(deserialize)

/**
 * Simplifies property assignment delegation for HxAttributes when setting attribute values from a string constant.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.setValue)
 */
@InternalAPI
public operator fun <T> SerializedMapValue<T>.setValue(thisRef: StringMap, property: KProperty<*>, value: T?) {
    if (value == null) {
        thisRef.remove(key)
    } else {
        thisRef[key] = serialize(value)
    }
}

/**
 * Treat the map key properties as a [Boolean]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.asBoolean)
 */
@InternalAPI
public fun String.asBoolean(): SerializedMapValue<Boolean> =
    SerializedMapValue(this, Boolean::toString, String::toBoolean)

/**
 * Simple type for handling serialization with [StringMap] delegation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.SerializedMapValue)
 */
@InternalAPI
public class SerializedMapValue<T>(
    internal val key: String,
    internal val serialize: (T) -> String,
    internal val deserialize: (String) -> T
)
