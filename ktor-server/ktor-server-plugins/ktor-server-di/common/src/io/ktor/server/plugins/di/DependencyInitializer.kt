/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Wraps the logic for creating a new instance of a dependency.
 *
 * Concrete types of this sealed interface are used to include some metadata regarding how they were registered.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer)
 */
public sealed interface DependencyInitializer {
    public val key: DependencyKey
    public val originKey: DependencyKey get() = key
    public fun resolve(resolver: DependencyResolver): Deferred<Any?>

    /**
     * An explicit dependency creation function for directly registered types.
     *
     * This includes caching of the instance value so resolved covariant keys do not trigger the creation multiple times.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Explicit)
     *
     * @property key The unique identifier of the dependency associated with this creation function.
     * @property init A lambda that implements the creation logic for the dependency.
     */
    public class Explicit(
        public override val key: DependencyKey,
        private val init: suspend DependencyResolver.() -> Any?
    ) : DependencyInitializer {
        private val deferred: AtomicRef<Deferred<Any?>?> = atomic(null)

        override fun resolve(resolver: DependencyResolver): Deferred<Any?> {
            return deferred.value ?: run {
                val newValue = resolver.lazyAsyncInit()
                if (deferred.compareAndSet(null, newValue)) {
                    newValue
                } else {
                    deferred.value!!
                }
            }
        }

        private fun DependencyResolver.lazyAsyncInit(): Deferred<Any?> =
            withCycleDetection {
                async(start = CoroutineStart.LAZY) { init() }
            }

        private fun <T> DependencyResolver.withCycleDetection(resolve: DependencyResolver.() -> T): T {
            val safeWrapper = when (this) {
                is SafeResolver -> this + key
                else -> SafeResolver(this, setOf(key))
            }
            return with(safeWrapper, resolve)
        }

        public fun derived(distance: Int): Implicit =
            Implicit(this, distance)
    }

    /**
     * Represents an implicitly registered dependency creation function that delegates to its explicit parent.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Implicit)
     *
     * @property origin The instance of [Explicit] that this class delegates creation logic to.
     * @property distance The distance from the original key.
     */
    public class Implicit(
        public val origin: Explicit,
        public val distance: Int,
    ) : DependencyInitializer by origin {
        override val originKey: DependencyKey get() = origin.originKey

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Implicit

            if (distance != other.distance) return false
            if (origin != other.origin) return false

            return true
        }

        override fun hashCode(): Int {
            var result = distance
            result = 31 * result + origin.hashCode()
            return result
        }
    }

    /**
     * Represents a specific implementation of [DependencyInitializer] that throws an exception
     * when there are multiple dependencies matching the given key, leading to an ambiguity.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Ambiguous)
     *
     * @property key The key for the dependency that caused the ambiguity.
     * @property functions A set of provider functions that caused the ambiguity.
     *
     * @throws AmbiguousDependencyException Always thrown when attempting to create a dependency
     * through the [create] method.
     */
    public data class Ambiguous(
        public override val key: DependencyKey,
        val functions: Set<DependencyInitializer>
    ) : DependencyInitializer {
        public companion object {
            /**
             * Instantiate a new [Ambiguous], if the provided functions are unique.
             *
             * This also will flatten any provided `AmbiguousInitializer`s.
             *
             * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Ambiguous.Companion.of)
             *
             * @param key The associated dependency key.
             * @param functions The functions to include in the resulting function.
             */
            public fun of(
                key: DependencyKey,
                vararg functions: DependencyInitializer
            ): DependencyInitializer {
                val functions = buildSet {
                    for (function in functions) {
                        when (function) {
                            is Ambiguous -> addAll(function.functions)
                            else -> add(function)
                        }
                    }
                }
                return functions.singleOrNull() ?: Ambiguous(key, functions)
            }
        }

        init {
            require(functions.size > 1) { "More than one function must be provided" }
        }

        override val originKey: DependencyKey get() =
            functions.first().originKey

        public fun clarify(predicate: (DependencyKey) -> Boolean): DependencyInitializer? =
            functions.singleOrNull { predicate(it.key) }

        public val implementations: List<DependencyKey> get() = functions.map { it.key }

        override fun resolve(resolver: DependencyResolver): Deferred<Any?> =
            CompletableDeferred<Any?>().also {
                it.completeExceptionally(AmbiguousDependencyException(this@Ambiguous))
            }

        public fun keyString(): String =
            "$key: ${implementations.joinToString()}"
    }

    /**
     * A placeholder that is used when a consumer is able to wait for a dependency provider to supply a true function.
     *
     * This should only be used when using the concurrent startup process, otherwise consumers may suspend indefinitely.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Missing)
     *
     * @property key The key for the dependency that caused the exception.
     *
     * @throws MissingDependencyException Always thrown when attempting to create a dependency
     * through the [create] method.
     */
    public class Missing(
        override val key: DependencyKey,
        private val resolver: DependencyResolver,
    ) : DependencyInitializer {
        private val deferred: CompletableDeferred<Any?> = CompletableDeferred()
        private val delegate: AtomicRef<DependencyInitializer?> = atomic(null)

        override fun resolve(resolver: DependencyResolver): Deferred<Any?> = deferred

        /**
         * We pipe the result of the provided function into the current function.
         *
         * This allows for suspending consumers to wait for a provider to supply a true function.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Missing.provide)
         */
        public fun provide(other: DependencyInitializer) {
            if (delegate.compareAndSet(null, other)) {
                deferred.completeWith(other.resolve(resolver))
            }
        }

        public fun throwMissing() {
            if (deferred.isActive) {
                deferred.completeExceptionally(MissingDependencyException(key))
            }
        }
    }

    /**
     * Represents a value-based dependency initializer, which resolves to a given value.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Value)
     *
     * @property key A unique identifier for this dependency.
     * @property value The actual value to be resolved when this dependency is accessed.
     */
    public data class Value(
        override val key: DependencyKey,
        val value: Any?
    ) : DependencyInitializer {
        override fun resolve(resolver: DependencyResolver): Deferred<Any?> =
            CompletableDeferred(value = value)
    }

    /**
     * Represents a no-op or absent dependency initializer.
     *
     * This class is used to resolve a dependency with a `null` value, indicating that
     * the dependency is intentionally absent or uninitialized.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyInitializer.Null)
     *
     * @property key The unique key associated with the dependency being resolved.
     */
    public data class Null(override val key: DependencyKey) : DependencyInitializer {
        private companion object {
            private val nullDeferred: CompletableDeferred<Any?> = CompletableDeferred(value = null)
        }

        override fun resolve(resolver: DependencyResolver): Deferred<Any?> = nullDeferred
    }
}
