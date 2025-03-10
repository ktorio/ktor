/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.utils.hierarchy

/**
 * Functional interface for mapping a dependency key to its covariant types.
 *
 * For example, `ArrayList<String> -> [List<String>, Collection<String>]`
 */
public fun interface DependencyKeyCovariance {
    public fun map(key: DependencyKey): List<DependencyKey>
}

/**
 * Standard covariance mapping for matching dependency keys to supertypes and implemented interfaces.
 */
public val Supertypes: DependencyKeyCovariance =
    DependencyKeyCovariance { key ->
        key.type.hierarchy().map { DependencyKey(it, key.name) }
    }

/**
 * Matches name-qualified dependency keys to the unnamed forms.
 *
 * This allows matching applicable types when there is no unnamed dependency key provided.
 *
 * This logic is not enabled by default.
 */
public val Unnamed: DependencyKeyCovariance =
    DependencyKeyCovariance { key ->
        key.name?.let { listOf(key.copy(name = null)) } ?: emptyList()
    }

/**
 * Helper operator function for combining covariance logic.
 *
 * For example, for all supertypes to match for both named and unnamed:
 * ```
 * Supertypes * Unnamed
 * ```
 */
public operator fun DependencyKeyCovariance.times(other: DependencyKeyCovariance) =
    DependencyKeyCovariance { key -> this.map(key).flatMap(other::map) }

/**
 * Helper operator function for combining covariance logic.
 *
 * For example, for only unnamed of a given type, plus all named covariant types:
 * ```
 * Supertypes + Unnamed
 * ```
 */
public operator fun DependencyKeyCovariance.plus(other: DependencyKeyCovariance) =
    DependencyKeyCovariance { key -> this.map(key) + other.map(key) }
