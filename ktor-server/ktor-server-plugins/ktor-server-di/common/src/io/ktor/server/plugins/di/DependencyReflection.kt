/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.util.reflect.*
import kotlin.reflect.KClass

/**
 * Provides reflection to dependency injection so that it may create new instances from class references.
 */
public interface DependencyReflection {
    /**
     * Creates a new instance of the specified class using the provided initialization logic.
     *
     * @param T The type of the instance to be created.
     * @param kClass The class reference for the type of object to instantiate.
     * @param init A lambda function that resolves dependencies required for the object creation
     *             using a `DependencyKey`.
     * @return A new instance of the specified class.
     */
    public fun <T : Any> create(kClass: KClass<T>, init: (DependencyKey) -> Any): T
}

/**
 * Provides an instance of the dependency associated with the specified [kClass].
 *
 * Uses the `create` method from the `DependencyResolver` to resolve and instantiate a dependency
 * of type [T] specified by the given [kClass].
 *
 * @param T The type of the dependency to be provided.
 * @param kClass The `KClass` representing the type of the dependency to be created or resolved.
 */
public inline fun <reified T : Any> DependencyProvider.provide(kClass: KClass<out T>) =
    provide { create(kClass) }

/**
 * Creates an instance of the specified type [T] using the dependency resolver.
 * This function utilizes the `DependencyReflection` mechanism to dynamically
 * construct an instance of the requested type, resolving dependencies as needed.
 *
 * @return An instance of the type [T].
 */
public inline fun <reified T : Any> DependencyResolver.create(): T =
    create(T::class)

/**
 * Creates or retrieves an instance of the specified type [T] from the [DependencyResolver].
 * If the instance does not already exist, it is created using reflection.
 *
 * @param T The type of the instance to be created or retrieved.
 * @param kClass The class reference representing the type of object to create or retrieve.
 * @return An instance of the specified type [T].
 */
public inline fun <reified T : Any> DependencyResolver.create(kClass: KClass<out T>) =
    getOrPut(DependencyKey(typeInfo<T>())) { reflection.create(kClass, ::get) }
