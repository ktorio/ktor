/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// We want these extensions to be available without importing
@file:Suppress("PackageDirectoryMismatch")

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.provider.Provider

fun <T> NamedDomainObjectContainer<T>.maybeRegister(name: String, configure: T.() -> Unit): NamedDomainObjectProvider<T> {
    return if (name in names) named(name, configure) else register(name, configure)
}

inline fun <T, R> Provider<out Iterable<T>>.mapValue(crossinline transform: (T) -> R): Provider<List<R>> =
    map { it.map(transform) }

inline fun <T, R> Provider<out Iterable<T>>.flatMapValue(crossinline transform: (T) -> Iterable<R>): Provider<List<R>> =
    map { it.flatMap(transform) }
