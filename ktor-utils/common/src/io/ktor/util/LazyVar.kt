package io.ktor.util

import kotlin.properties.*
import kotlin.reflect.*

/** Like `by lazy`, but usable with `var` (i.e. mutable). */
fun <T> lazyVar(initializer: () -> T): ReadWriteProperty<Any?, T> = LazyVar(initializer)

private class LazyVar<T>(initializer: () -> T) : ReadWriteProperty<Any?, T> {
    private var overriddenValue: T? = null
    private var hasOverriddenValue = false
    private val initializerValue by lazy(initializer)

    @Suppress("UNCHECKED_CAST")
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        if (hasOverriddenValue) overriddenValue as T else initializerValue

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        overriddenValue = value
        hasOverriddenValue = true
    }
}
