/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ktlint:standard:max-line-length")

package io.ktor.server.plugins.di

import io.ktor.utils.io.*

/**
 * Registers a dependency provider that takes no parameters and returns a value of type [E].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param function A function that creates and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
@KtorDsl public inline fun <reified E> DependencyRegistry.provide(
    crossinline function: suspend () -> E
): DependencyRegistry.KeyContext<E> = provide {
    function()
}

/**
 * Registers a dependency provider that takes one input parameter and returns a value of type [E].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param I1 The type of the first input parameter
 * @param function A function that takes one parameter of type [I1] and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
@KtorDsl public inline fun <reified E, reified I1> DependencyRegistry.provide(
    crossinline function: suspend (I1) -> E
): DependencyRegistry.KeyContext<E> = provide { function(resolve()) }

/**
 * Registers a dependency provider that takes two input parameters and returns a value of type [E].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param I1 The type of the first input parameter
 * @param I2 The type of the second input parameter
 * @param function A function that takes two parameters and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
@KtorDsl public inline fun <reified E, reified I1, reified I2> DependencyRegistry.provide(
    crossinline function: suspend (I1, I2) -> E
): DependencyRegistry.KeyContext<E> = provide { function(resolve(), resolve()) }

/**
 * Registers a dependency provider that takes three input parameters and returns a value of type [E].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param I1 The type of the first input parameter
 * @param I2 The type of the second input parameter
 * @param I3 The type of the third input parameter
 * @param function A function that takes three parameters and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
@KtorDsl public inline fun <reified E, reified I1, reified I2, reified I3> DependencyRegistry.provide(
    crossinline function: suspend (I1, I2, I3) -> E
): DependencyRegistry.KeyContext<E> = provide { function(resolve(), resolve(), resolve()) }

/**
 * Registers a dependency provider that takes four input parameters and returns a value of type [E].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param I1 The type of the first input parameter
 * @param I2 The type of the second input parameter
 * @param I3 The type of the third input parameter
 * @param I4 The type of the fourth input parameter
 * @param function A function that takes four parameters and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
@KtorDsl public inline fun <reified E, reified I1, reified I2, reified I3, reified I4> DependencyRegistry.provide(
    crossinline function: suspend (I1, I2, I3, I4) -> E
): DependencyRegistry.KeyContext<E> = provide { function(resolve(), resolve(), resolve(), resolve()) }

/**
 * Registers a dependency provider that takes five input parameters and returns a value of type [E].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param I1 The type of the first input parameter
 * @param I2 The type of the second input parameter
 * @param I3 The type of the third input parameter
 * @param I4 The type of the fourth input parameter
 * @param I5 The type of the fifth input parameter
 * @param function A function that takes five parameters and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
@KtorDsl public inline fun <reified E, reified I1, reified I2, reified I3, reified I4, reified I5> DependencyRegistry.provide(
    crossinline function: suspend (I1, I2, I3, I4, I5) -> E
): DependencyRegistry.KeyContext<E> = provide { function(resolve(), resolve(), resolve(), resolve(), resolve()) }

/**
 * Registers a dependency provider that takes six input parameters and returns a value of type [E].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 *
 * @param E The type of dependency to be provided
 * @param I1 The type of the first input parameter
 * @param I2 The type of the second input parameter
 * @param I3 The type of the third input parameter
 * @param I4 The type of the fourth input parameter
 * @param I5 The type of the fifth input parameter
 * @param I6 The type of the sixth input parameter
 * @param function A function that takes six parameters and returns an instance of [E]
 * @return A [DependencyRegistry.KeyContext] for further configuration of the dependency
 */
@KtorDsl public inline fun <reified E, reified I1, reified I2, reified I3, reified I4, reified I5, reified I6> DependencyRegistry.provide(
    crossinline function: suspend (I1, I2, I3, I4, I5, I6) -> E
): DependencyRegistry.KeyContext<E> = provide {
    function(resolve(), resolve(), resolve(), resolve(), resolve(), resolve())
}
