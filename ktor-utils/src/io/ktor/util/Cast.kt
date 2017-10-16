package io.ktor.util

import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun <T : Any> Any.cast(type: KClass<T>) = if (type.java.isInstance(this)) this as T else throw ClassCastException("${this::class} couldn't be cast to $type")

inline fun <reified T : Any> Any.cast() = cast(T::class)
fun <T : Any> newInstance(type: KClass<T>): T =
        type.constructors.firstOrNull { it.parameters.isEmpty() }?.call() ?: throw IllegalArgumentException("Type $type should have no-arg constructor or use session(T) instead")

inline fun <reified T> Any?.safeAs(): T? = this as? T
