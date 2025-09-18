/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.DependencyConflictResult.*

/**
 * Defines a policy for resolving conflicts between two dependency creation functions.
 *
 * This mechanism is used to manage dependency resolutions in scenarios where multiple
 * initializers are registered for the same dependency key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyConflictPolicy)
 */
public fun interface DependencyConflictPolicy {

    /**
     * Resolves a conflict between two dependency creation functions.
     *
     * This method determines the appropriate `DependencyConflictResult` for handling
     * the conflict between the previously registered dependency creation function
     * and the currently provided one.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyConflictPolicy.resolve)
     *
     * @param prev The previously registered dependency creation function.
     * @param current The newly provided dependency creation function.
     * @return The result of the conflict resolution, encapsulated in a `DependencyConflictResult`.
     *
     * @see DependencyConflictResult
     */
    public fun resolve(prev: DependencyInitializer, current: DependencyInitializer): DependencyConflictResult
}

/**
 * Represents the result of a dependency conflict resolution within a dependency injection system.
 *
 * A conflict occurs when two or more dependency creation functions are associated with the same
 * dependency key. This sealed interface defines possible ways to resolve these conflicts.
 *
 * The implementations include:
 * - `KeepPrevious`: Retain the previously registered dependency.
 * - `KeepNew`: Replace the previous dependency with the newly registered one.
 * - `Ambiguous`: Mark the conflict as ambiguous and unresolved.
 * - `Conflict`: Indicate a detected irreconcilable conflict that cannot be resolved.
 * - `Replace`: Replace the existing dependency with a specific creation function.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyConflictResult)
 */
public sealed interface DependencyConflictResult {
    public data object KeepPrevious : DependencyConflictResult
    public data object KeepNew : DependencyConflictResult
    public data object Ambiguous : DependencyConflictResult
    public data object Conflict : DependencyConflictResult
    public data class Replace(public val function: DependencyInitializer) : DependencyConflictResult
}

/**
 * The default conflict policy ensures that explicit declarations take precedence over implicit types.
 *
 * When two declarations are made for the same type, a duplicate exception is thrown.
 * When there are multiple declarations that match the same implicit keys, then an ambiguous exception is thrown.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DefaultConflictPolicy)
 */
public val DefaultConflictPolicy: DependencyConflictPolicy = DependencyConflictPolicy { prev, current ->
    require(current !is DependencyInitializer.Ambiguous) { "Unexpected ambiguous function supplied" }
    val diff = current.distance() - prev.distance()
    when {
        diff < 0 -> KeepNew
        diff > 0 || current == prev -> KeepPrevious
        prev is DependencyInitializer.Explicit -> Conflict
        else -> Ambiguous
    }
}

/**
 * During testing, we ignore conflicts.
 * This allows for replacing base implementations with mock values.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.IgnoreConflicts)
 */
public val IgnoreConflicts: DependencyConflictPolicy = DependencyConflictPolicy { prev, current ->
    when (val result = DefaultConflictPolicy.resolve(prev, current)) {
        is Conflict -> KeepPrevious
        else -> result
    }
}

private fun DependencyInitializer.distance(): Int = when (this) {
    is DependencyInitializer.Explicit,
    is DependencyInitializer.Value -> -1
    is DependencyInitializer.Implicit -> distance
    is DependencyInitializer.Ambiguous -> functions.minOf { it.distance() }
    is DependencyInitializer.Missing,
    is DependencyInitializer.Null -> Int.MAX_VALUE
}
