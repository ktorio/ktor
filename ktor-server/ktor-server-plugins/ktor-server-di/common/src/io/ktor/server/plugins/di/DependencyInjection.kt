/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.PluginBuilder
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.plugins.di.utils.ClasspathReference
import io.ktor.server.plugins.di.utils.installReference
import io.ktor.util.AttributeKey
import io.ktor.util.reflect.TypeInfo
import kotlin.reflect.KClass

/**
 * A Ktor application plugin for managing the registration and resolution of module dependencies.
 *
 * The following properties are configurable:
 * 1. `provider`: the logic for registering new types
 * 2. `resolution`: the function for validating the provided types and creating the dependency map
 * 3. `reflection`: controls how objects are initialized from type references
 *
 * You can declare dependencies using the dependencies DSL:
 *
 * ```kotlin
 * fun Application.databaseModule() {
 *     dependencies {
 *         provide<Database> { PostgresDatabase(resolve("connectionUrl")) }
 *     }
 * }
 * ```
 *
 * Or through configuration:
 *
 * ```yaml
 * ktor:
 *   application:
 *     dependencies:
 *       - com.example.db.PostgresDatabase
 * ```
 *
 * Each item declared via configuration can be a reference to a class or a
 * top-level function.
 *
 * Resolving dependencies in application modules is also achieved through the DSL:
 *
 * ```kotlin
 * fun Application.routing() {
 *     val database: Database by dependencies
 *     val repository: MessagesRepository = dependencies.create()
 * }
 * ```
 *
 * Alternatively, they can be supplied automatically through parameters.
 */
public val DI: ApplicationPlugin<DependencyInjectionConfig> =
    createApplicationPlugin("DI", ::DependencyInjectionConfig) {
        val configuredDependencyReferences =
            environment.config.propertyOrNull("ktor.application.dependencies")
                ?.getList()
                ?.map { ClasspathReference(it) }
                .orEmpty()

        val provider = if (isTestEngine() && !pluginConfig.providerChanged) {
            MapDependencyProvider(conflictPolicy = LastEntryWinsPolicy)
        } else {
            pluginConfig.provider
        }

        var registry = DependencyRegistryImpl(
            provider,
            pluginConfig.resolution,
            pluginConfig.reflection,
        )

        with(application) {
            for (reference in configuredDependencyReferences) {
                installReference(registry, reference)
            }
            monitor.subscribe(ApplicationStarted) {
                registry.validate()
            }
            attributes.put(DependencyRegistryKey, registry)
        }
    }

private fun PluginBuilder<*>.isTestEngine(): Boolean =
    application.engine::class.simpleName == "TestApplicationEngine"

public val DependencyRegistryKey: AttributeKey<DependencyRegistry> =
    AttributeKey<DependencyRegistry>("DependencyRegistry")

public expect val DefaultReflection: DependencyReflection

/**
 * Automatically failing implementation of [DependencyReflection] that is used by default
 * when unsupported on the current platform.
 *
 * Optionally, this can be used when you do not wish to allow reflection for your project.
 */
public object NoReflection : DependencyReflection {
    override fun <T : Any> create(kClass: KClass<T>, init: (DependencyKey) -> Any): T =
        throw DependencyInjectionException("A call to create a new instance was attempted, but reflection is disabled")
}

/**
 * Configuration class for dependency injection settings and behavior customization.
 *
 * This class allows customization of the reflection mechanism, the dependency provider,
 * and the resolution strategy used within the dependency injection framework.
 *
 * @property reflection Specifies the mechanism used to create new instances of dependencies via reflection.
 *                      Defaults to `DefaultReflection`.
 * @property provider Manages the registration of dependency initializers.
 *                    Defaults to a `MapDependencyProvider`.
 * @property resolution Defines the strategy for resolving dependencies.
 *                      Defaults to `DefaultDependencyResolution`.
 *
 * @see DependencyReflection
 * @see DependencyProvider
 * @see DependencyResolution
 * @see MapDependencyProvider
 */
public class DependencyInjectionConfig {
    internal var providerChanged = false
    public var reflection: DependencyReflection = DefaultReflection
    public var provider: DependencyProvider = MapDependencyProvider()
        set(value) {
            field = value
            providerChanged = true
        }
    public var resolution: DependencyResolution = DefaultDependencyResolution

    /**
     * Convenience function for overriding the different logical aspects of the [DependencyProvider].
     *
     * This is shorthand for reassigning the `provider` variable with a new [MapDependencyProvider] with custom fields.
     *
     * @see ProviderScope
     */
    public fun provider(config: ProviderScope.() -> Unit) {
        provider = ProviderScope().apply(config).let { (keyMapping, conflictPolicy, onConflict) ->
            MapDependencyProvider(keyMapping, conflictPolicy, onConflict)
        }
    }

    /**
     * Configuration scope for customizing dependency injection behavior, used to specify key mapping,
     * conflict resolution strategies, and conflict handling mechanisms.
     *
     * @property keyMapping Specifies the mapping strategy used to handle covariance between dependency keys.
     *                      Defaults to `Supertypes`, which maps dependency keys to their covariant types.
     * @property conflictPolicy Defines the policy used to determine how conflicts between dependencies with
     *                          the same key are resolved.
     *                          Defaults to `DefaultConflictPolicy`.
     * @property onConflict Specifies a handler invoked when a dependency conflict scenario arises due
     *                      to multiple definitions for the same dependency key.
     *                      By default, this throws a `DuplicateDependencyException`.
     */
    public data class ProviderScope(
        public var keyMapping: DependencyKeyCovariance = Supertypes,
        public var conflictPolicy: DependencyConflictPolicy = DefaultConflictPolicy,
        public var onConflict: (DependencyKey) -> Unit = { throw DuplicateDependencyException(it) }
    )
}

/**
 * Unique key for a dependency.
 */
public data class DependencyKey(val type: TypeInfo, val name: String? = null) {
    override fun toString(): String = buildString {
        append(type.kotlinType ?: type.type)
        if (name != null) {
            append("(name = \"$name\")")
        }
    }
}

/**
 * Common parent for dependency injection problem.
 */
public open class DependencyInjectionException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Thrown when attempting to resolve a dependency that was not declared.
 */
public class MissingDependencyException(key: DependencyKey) :
    DependencyInjectionException("Could not resolve dependency for `$key`")

/**
 * Thrown when a dependency is declared more than once.
 */
public class DuplicateDependencyException(key: DependencyKey) :
    DependencyInjectionException("Attempted to redefine dependency `$key`")

/**
 * Thrown when there are two or more implicit dependencies that match the given key.
 */
public class AmbiguousDependencyException(key: DependencyKey, keys: Collection<DependencyKey>) :
    DependencyInjectionException("Cannot decide which value for $key. Possible implementations: $keys")

/**
 * Thrown when resolving a given dependency loops back on itself.
 */
public class CircularDependencyException(internal val keys: Collection<DependencyKey>) :
    DependencyInjectionException("Circular dependency found: ${keys.joinToString(" -> ")}")

/**
 * Thrown when attempting to provide a dependency AFTER the dependency map is created.
 */
public class OutOfOrderDependencyException(key: DependencyKey) :
    DependencyInjectionException("Attempted to define $key after dependencies were resolved")

/**
 * Thrown when attempting to instantiate an abstract type using reflection.
 */
public class DependencyAbstractTypeConstructionException(
    qualifiedName: String,
) : DependencyInjectionException("Cannot instantiate abstract type: $qualifiedName")

/**
 * Thrown when a static reference cannot be resolved from the configuration file.
 */
public class InvalidDependencyReferenceException internal constructor(
    message: String,
    reference: ClasspathReference,
    cause: Throwable? = null
) : DependencyInjectionException("$message: $reference", cause)
