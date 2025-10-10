/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Deferred
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * A central registry for managing and resolving dependencies within a dependency injection context.
 *
 * The `DependencyRegistry` class acts as both a `DependencyProvider` and a `DependencyResolver`.
 * It facilitates the registration of dependencies through a `DependencyProvider` as well as the resolution
 * and validation of dependencies using the provided resolver mechanism. This registry also supports
 * reflective creation of instances and type-safe access to registered dependencies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry)
 *
 * @param resolver The delegate resolver for finding registered dependencies.
 * @param provider The delegate provider responsible for registering new dependencies..
 * @param reflection A reflection implementation that supports dynamic instantiation of classes.
 */
@KtorDsl
public class DependencyRegistry(
    public val resolver: DependencyResolver,
    public val provider: DependencyProvider,
) : DependencyProvider by provider, DependencyResolver by resolver {
    /**
     * A map of required dependencies to be validated during startup.
     *
     * Used for lazily retrieved values.
     */
    internal val requirements = mutableMapOf<DependencyKey, DependencyReference>()

    /**
     * A map of shutdown hooks to be executed during shutdown.
     *
     * Supplied by the "cleanup" function.
     */
    internal val shutdownHooks = mutableMapOf<DependencyKey, (Any?) -> Unit>()

    /**
     * Get the dependency from the map for the key represented by the type (and optionally, with the given name).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.resolve)
     */
    public suspend inline fun <reified T> resolve(key: String? = null): T =
        get(DependencyKey<T>(key))

    /**
     * Get a deferred dependency from the map for the key represented by the type (and optionally, with the given name).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.resolveDeferred)
     */
    public inline fun <reified T> resolveDeferred(key: String? = null): Deferred<T> =
        getDeferred(DependencyKey<T>(key))

    /**
     * Indicates that the given dependency is required.
     *
     * The DI plugin enforces this at startup.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.require)
     */
    public fun require(key: DependencyKey) {
        requirements += key to DependencyReference()
    }

    /**
     * Provides a delegated property for accessing a dependency from a [DependencyRegistry].
     * This operator function allows property delegation, ensuring the required dependency is
     * registered and retrievable through the registry.
     *
     * Example usage:
     * ```
     * val repository: Repository<Message> by dependencies
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.provideDelegate)
     *
     * @param thisRef The receiver to which the property is being delegated. This parameter
     * is not used in the actual implementation.
     * @param prop The property for which the delegate is being requested.
     * @return A [ReadOnlyProperty] that provides access to the resolved dependency of type [T].
     * @throws DependencyInjectionException If the dependency required by [prop] is not resolvable
     * during access.
     */
    public inline operator fun <reified T> provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ): ReadOnlyProperty<Any?, T> {
        val key = DependencyKey<T>()
            .also(::require)
        return ReadOnlyProperty { _, _ ->
            getBlocking(key)
        }
    }

    /**
     * Provides an instance of the dependency associated with the specified [kClass].
     *
     * Uses the `create` method from the `DependencyResolver` to resolve and instantiate a dependency
     * of type [T] specified by the given [kClass].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.provide)
     *
     * @param T The type of the dependency to be provided.
     * @param kClass The `KClass` representing the type of the dependency to be created or resolved.
     */
    public inline fun <reified T : Any> provide(kClass: KClass<out T>): KeyContext<T> =
        provide<T> { create(kClass) }

    /**
     * Creates a new `KeyContext` for the specified type [T] and an optional name.
     * The given [handler] is invoked on the created `KeyContext`, allowing configuration
     * such as defining a provider or cleanup logic for the dependency.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.key)
     *
     * @param T The type of the dependency being handled.
     * @param name An optional name associated with the dependency. Defaults to `null` if not provided.
     * @param handler A lambda that defines the actions to be performed on the created `KeyContext`.
     * @return A `KeyContext` instance representing the defined key and its associated actions.
     */
    public inline fun <reified T> key(name: String? = null, noinline handler: KeyContext<T>.() -> Unit): KeyContext<T> =
        KeyContext<T>(DependencyKey<T>(name)).also(handler)

    /**
     * Basic call for providing a dependency, like `provide<Service> { ServiceImpl() }`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.provide)
     */
    public inline fun <reified T> provide(
        name: String? = null,
        noinline provide: suspend DependencyResolver.() -> T?
    ): KeyContext<T> =
        key<T>(name) { provide(provide) }

    public inline fun <reified T> cleanup(name: String? = null, noinline cleanup: (T) -> Unit): KeyContext<T> =
        key<T>(name) { cleanup(cleanup) }

    public fun cleanup(key: DependencyKey, cleanup: (Any?) -> Unit) {
        require(!shutdownHooks.contains(key)) {
            "Shutdown hook already registered for $this"
        }
        shutdownHooks[key] = cleanup
    }

    /**
     * DSL class for performing multiple actions for the given key and type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyRegistry.KeyContext)
     */
    @KtorDsl
    public inner class KeyContext<T>(public val key: DependencyKey) {
        public infix fun provide(provide: suspend DependencyResolver.() -> T?) {
            this@DependencyRegistry.set(key, provide)
        }

        @Suppress("UNCHECKED_CAST")
        public infix fun cleanup(cleanup: (T) -> Unit) {
            this@DependencyRegistry.cleanup(key) { cleanup(it as T) }
        }
    }
}

/**
 * DSL helper for declaring dependencies with `dependencies {}` block.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.dependencies)
 */
@KtorDsl
public fun <T> Application.dependencies(action: DependencyRegistry.() -> T): T =
    dependencies.action()

public var Application.dependencies: DependencyRegistry
    get() {
        if (!attributes.contains(DependencyRegistryKey)) {
            install(DI)
        }
        return attributes[DependencyRegistryKey]
    }
    set(value) {
        attributes.put(DependencyRegistryKey, value)
    }

/**
 * Used for tracing references to dependency keys in the application.
 *
 * Both called for lazy `resolve` access and `provide` for easier debugging.
 */
internal class DependencyReference : Throwable() {

    /**
     * Gets the first external API line from the stack trace.
     */
    fun externalTraceLine(): String =
        externalTrace().substringBefore('\n').trim()

    /**
     * Get the stack trace string from the point of the first external API frame.
     */
    fun externalTrace(): String =
        stackTraceToString()
            .substringAfterLast("io.ktor")
            .substringAfter('\n')
}
