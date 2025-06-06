/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
import io.ktor.server.plugins.di.utils.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
public val DI: ApplicationPlugin<DependencyInjectionConfig> =
    createApplicationPlugin("DI", ::DependencyInjectionConfig) {
        val configuredDependencyReferences =
            environment.config.propertyOrNull("ktor.application.dependencies")
                ?.getList()
                ?.map { ClasspathReference(it) }
                .orEmpty()
        val startupMode =
            environment.config.propertyOrNull("ktor.application.startup")?.getString()

        val configMap = ConfigurationDependencyMap(application.environment.config)
        var extensionMap = pluginConfig.dependenciesMap?.let { it + configMap } ?: configMap
        for (extension in loadMapExtensions()) {
            extensionMap += extension.get(application)
        }
        val onShutdown = pluginConfig.onShutdown

        val exceptionHandler = CoroutineExceptionHandler { context, e ->
            environment.log.error("Error encountered during dependency resolution", e)
        }
        val coroutineContext =
            application.coroutineContext +
                CoroutineName("dependency-injection-tasks") +
                Dispatchers.Default.limitedParallelism(1) +
                exceptionHandler
        val map: DependencyInitializerMap = mutableMapOf()
        val reflection = pluginConfig.reflection
        val useSuspend = when (pluginConfig.onMissing) {
            DependencyInjectionConfig.OnMissing.Suspend -> true
            DependencyInjectionConfig.OnMissing.Fail -> false
            else -> startupMode?.equals("concurrent", ignoreCase = true) ?: false
        }
        val resolver = MapDependencyResolver(
            map,
            extensionMap,
            reflection,
            useSuspend,
            coroutineContext,
        )
        val conflictPolicy = pluginConfig.conflictPolicy
            ?: if (isTestEngine()) IgnoreConflicts else DefaultConflictPolicy
        val provider = MapDependencyProvider(
            map = map,
            keyMapping = pluginConfig.keyMapping,
            conflictPolicy = conflictPolicy,
            onConflict = pluginConfig.onConflict,
        )
        var registry = DependencyRegistry(resolver, provider)

        with(application) {
            // First, we install all references from the configuration file
            for (reference in configuredDependencyReferences) {
                installReference(registry, reference)
            }
            // If suspended on startup, consumers will now fail with missing
            monitor.subscribe(ApplicationModulesLoading) {
                resolver.stopWaiting()
            }
            // Validate any lazy-loaded dependencies before starting the server
            monitor.subscribe(ApplicationModulesLoaded) {
                val exceptions = mutableListOf<Pair<DependencyKey, Throwable>>()
                for ((key, source) in registry.requirements) {
                    try {
                        registry.getBlocking<Any?>(key)
                    } catch (e: Throwable) {
                        environment.log.error("Cannot resolve $key\n${source.externalTrace()}")
                        exceptions += key to e
                    }
                }
                when (exceptions.size) {
                    0 -> environment.log.debug("All dependencies resolved successfully")
                    else -> {
                        environment.log.error(
                            buildString {
                                append("Dependency resolution failed:")
                                exceptions.forEach { (key, e) ->
                                    append("\n  - $key: ${formatError(e)}")
                                }
                            }
                        )
                        throw DependencyInjectionException(
                            "Some dependencies could not be resolved; check logs for details"
                        )
                    }
                }
            }
            monitor.subscribe(ApplicationStopped) {
                for (key in map.keys.reversed()) {
                    try {
                        val instance = registry.getDeferred<Any?>(key).tryGetCompleted() ?: continue
                        registry.shutdownHooks[key]?.invoke(instance)
                        onShutdown(key, instance)
                    } catch (e: Throwable) {
                        environment.log.warn("Exception during cleanup for $key; continuing", e)
                    }
                }
            }

            attributes.put(DependencyRegistryKey, registry)
        }
    }

private fun formatError(e: Throwable): String =
    when (e) {
        is MissingDependencyException -> "Missing declaration"
        is CircularDependencyException -> "Circular dependency: ${e.keys.joinToString(" -> ")}"
        is DuplicateDependencyException -> "Conflicting declaration"
        is AmbiguousDependencyException -> "Ambiguous dependency for ${e.function.keyString()}"
        else -> e.message ?: ("Unknown error" + e.stackTraceToString().let { "\n$it" })
    }

private fun PluginBuilder<*>.isTestEngine(): Boolean =
    application.engine::class.simpleName == "TestApplicationEngine"

public val DependencyRegistryKey: AttributeKey<DependencyRegistry> =
    AttributeKey<DependencyRegistry>("DependencyRegistry")

public expect val DefaultReflection: DependencyReflection

internal expect fun loadMapExtensions(): List<DependencyMapExtension>

/**
 * Automatically failing implementation of [DependencyReflection] that is used by default
 * when unsupported on the current platform.
 *
 * Optionally, this can be used when you do not wish to allow reflection for your project.
 */
public object NoReflection : DependencyReflection {
    override suspend fun <T : Any> create(kClass: KClass<T>, init: suspend (DependencyKey) -> Any): T =
        throw DependencyInjectionException("A call to create a new instance was attempted, but reflection is disabled")
}

/**
 * Configuration class for dependency injection settings and behavior customization.
 *
 * This class allows customization of the reflection mechanism, the dependency provider,
 * and various behaviors of the dependency injection framework.
 *
 * @property reflection Specifies the mechanism used to create new instances of dependencies via reflection.
 *                      Defaults to `DefaultReflection`.
 * @property keyMapping Defines how type covariance is handled when resolving dependencies.
 *                      Defaults to `DefaultKeyCovariance`.
 * @property conflictPolicy Determines how conflicts between dependency declarations are handled.
 *                          Defaults to `DefaultConflictPolicy` in normal mode or `IgnoreConflicts` in test mode.
 * @property onConflict A callback invoked when a duplicate dependency is detected.
 *                      Defaults to throwing a `DuplicateDependencyException`.
 * @property onMissing Determines behavior when a dependency is not found.
 *                     When set to `Suspend`, resolution will wait for later declarations.
 *                     When set to `Fail`, missing dependencies immediately fail.
 * @property onShutdown A callback invoked when the application stops.
 *                      Defaults to closing all instances of `AutoCloseable`.
 *
 * @see DependencyReflection
 * @see DependencyKeyCovariance
 * @see DependencyConflictPolicy
 * @see OnMissing
 */
public class DependencyInjectionConfig {
    public var reflection: DependencyReflection = DefaultReflection
    public var keyMapping: DependencyKeyCovariance = DefaultKeyCovariance
    public var conflictPolicy: DependencyConflictPolicy? = null
    public var onConflict: (DependencyKey) -> Nothing = { throw DuplicateDependencyException(it) }
    public var onMissing: OnMissing? = null

    public var onShutdown: (DependencyKey, Any?) -> Unit = { _, instance ->
        (instance as? AutoCloseable)?.close()
    }
    internal var dependenciesMap: DependencyMap? = null

    /**
     * Include an additional source for dependencies.
     *
     * Sources added this way take precedence in the order of inclusion.
     *
     * Declared dependencies have precedence over all sources included this way.
     *
     * For example:
     * ```kotlin
     * install(DI) {
     *     // Add /dev/null file as baseline fallback
     *     include(DependencyMapImpl(mapOf(DependencyKey(File::class.typeInfo() to File("/dev/null")))))
     *     // If File is available in this Koin module, use it instead
     *     include(KoinDependencies(moduleReference))
     * }
     * // This declaration will be used before the included maps
     * dependencies.provide { File("/opt/output") }
     * ```
     */
    public fun include(map: DependencyMap) {
        dependenciesMap = if (dependenciesMap == null) map else dependenciesMap!! + map
    }

    public enum class OnMissing {
        Suspend,
        Fail
    }
}

/**
 * Unique key for a dependency.
 */
public data class DependencyKey(
    public val type: TypeInfo,
    public val name: String? = null,
    public val qualifier: Any? = null,
) {
    override fun toString(): String = buildString {
        append(type.kotlinType ?: type.type)
        if (name != null) {
            append("(\"$name\")")
        }
    }
}

/**
 * Convenience function for `DependencyKey(typeInfo<T>(), name, qualifier)`.
 */
public inline fun <reified T> DependencyKey(
    name: String? = null,
    qualifier: Any? = null,
): DependencyKey = DependencyKey(typeInfo<T>(), name, qualifier)

/**
 * Determines if the type associated with a `DependencyKey` is nullable.
 *
 * This function checks whether the `kotlinType` property of the `type` in the `DependencyKey`
 * is marked as nullable. If there is no `kotlinType`, it will return `false`.
 */
public fun DependencyKey.isNullable(): Boolean =
    type.kotlinType?.isMarkedNullable == true

/**
 * Common parent for dependency injection problems.
 */
public open class DependencyInjectionException(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)

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
public class AmbiguousDependencyException(
    public val function: DependencyInitializer.Ambiguous
) : DependencyInjectionException(
    "Cannot decide which value for ${function.key}: ${function.implementations}"
)

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
