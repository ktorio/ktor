/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

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
 * Creates an instance of the specified type [T] using the dependency resolver.
 * This function uses the `DependencyReflection` mechanism to dynamically
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
public inline fun <reified T : Any> DependencyResolver.create(kClass: KClass<out T>): T =
    getOrPut(DependencyKey<T>()) { reflection.create(kClass, ::get) }
