/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import kotlin.reflect.KFunction

/**
 * Registers a dependency provider for the given function reference with return type [E].
 *
 * All arguments will be resolved automatically via the registry.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param function A function reference that creates and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
public inline fun <reified E> DependencyRegistry.provide(
    function: KFunction<E>
): DependencyRegistry.KeyContext<E> = provide {
    reflection.call(function, ::get)
}
