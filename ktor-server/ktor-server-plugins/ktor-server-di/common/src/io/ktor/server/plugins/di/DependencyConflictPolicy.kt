/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.DependencyConflictResult.Ambiguous
import io.ktor.server.plugins.di.DependencyConflictResult.Conflict
import io.ktor.server.plugins.di.DependencyConflictResult.KeepNew
import io.ktor.server.plugins.di.DependencyConflictResult.KeepPrevious

/**
 * Defines a policy for resolving conflicts between two dependency creation functions.
 *
 * This mechanism is used to manage dependency resolutions in scenarios where multiple
 * initializers are registered for the same dependency key.
 */
public fun interface DependencyConflictPolicy {

    /**
     * Resolves a conflict between two dependency creation functions.
     *
     * This method determines the appropriate `DependencyConflictResult` for handling
     * the conflict between the previously registered dependency creation function
     * and the currently provided one.
     *
     * @param prev The previously registered dependency creation function.
     * @param current The newly provided dependency creation function.
     * @return The result of the conflict resolution, encapsulated in a `DependencyConflictResult`.
     *
     * @see DependencyConflictResult
     */
    public fun resolve(prev: DependencyCreateFunction, current: DependencyCreateFunction): DependencyConflictResult
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
 */
public sealed interface DependencyConflictResult {
    public object KeepPrevious : DependencyConflictResult
    public object KeepNew : DependencyConflictResult
    public object Ambiguous : DependencyConflictResult
    public object Conflict : DependencyConflictResult
    public class Replace(public val function: DependencyCreateFunction) : DependencyConflictResult
}

/**
 * The default conflict policy ensures that explicit declarations take precedence over implicit types.
 *
 * When two declarations are made for the same type, a duplicate exception is thrown.
 * When there are multiple declarations that match the same implicit keys, then an ambiguous exception is thrown.
 */
public val DefaultConflictPolicy: DependencyConflictPolicy = DependencyConflictPolicy { prev, current ->
    require(current !is AmbiguousCreateFunction) { "Unexpected ambiguous function supplied" }
    when (prev) {
        is AmbiguousCreateFunction,
        is ImplicitCreateFunction -> current.ifImplicit { Ambiguous } ?: KeepNew
        is ExplicitCreateFunction -> current.ifImplicit { KeepPrevious } ?: Conflict
    }
}

/**
 * During testing, we simply override previously declared values.
 * This allows for replacing base implementations with mock values.
 */
public val LastEntryWinsPolicy: DependencyConflictPolicy = DependencyConflictPolicy { prev, current ->
    require(current !is AmbiguousCreateFunction) { "Unexpected ambiguous function supplied" }
    when (prev) {
        is AmbiguousCreateFunction,
        is ImplicitCreateFunction -> KeepNew
        is ExplicitCreateFunction -> current.ifImplicit { KeepPrevious } ?: KeepNew
    }
}
