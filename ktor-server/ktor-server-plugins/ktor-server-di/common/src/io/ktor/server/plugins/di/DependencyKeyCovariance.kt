/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.utils.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Functional interface for mapping a dependency key to its covariant types.
 *
 * For example, `ArrayList<String> -> [ArrayList<String>, List<String>, Collection<String>]`
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyKeyCovariance)
 */
public fun interface DependencyKeyCovariance {
    public fun map(key: DependencyKey, distance: Int): Sequence<KeyMatch>
}

internal typealias KeyMatch = Pair<DependencyKey, Int>

internal val KeyMatch.key: DependencyKey get() = first
internal val KeyMatch.distance: Int get() = second

/**
 * Standard covariance mapping for matching dependency keys to supertypes and implemented interfaces.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.Supertypes)
 */
@OptIn(InternalAPI::class)
public val Supertypes: DependencyKeyCovariance =
    DependencyKeyCovariance { key, start ->
        var distance = start
        key.type.hierarchy().map {
            DependencyKey(it, key.name) to distance++
        }
    }

/**
 * Matches name-qualified dependency keys to the unnamed forms.
 *
 * This allows matching applicable types when there is no unnamed dependency key provided.
 *
 * This logic is not enabled by default.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.Unnamed)
 */
public val Unnamed: DependencyKeyCovariance =
    DependencyKeyCovariance { key, distance ->
        sequence {
            yield(key to distance)
            key.name?.let {
                yield(key.copy(name = null) to distance.inc())
            }
        }
    }

/**
 * A predefined implementation of [DependencyKeyCovariance] that creates a covariant dependency key
 * based on the given key. It generates a list containing a single dependency key with a new
 * [TypeInfo] that retains the same type and Kotlin type as the original key.
 *
 * The primary purpose of this value is to facilitate support for nullable types or variant type
 * mappings within dependency injection.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.Nullables)
 */
@OptIn(InternalAPI::class)
public val Nullables: DependencyKeyCovariance =
    DependencyKeyCovariance { key, distance ->
        sequence {
            yield(key to distance)
            key.type.toNullable()?.let { nullableVariant ->
                yield(key.copy(type = nullableVariant) to distance.inc())
            }
        }
    }

/**
 * A [DependencyKeyCovariance] that generates supertypes for out type arguments of parameterized types.
 *
 * For example, `Pair<String, Int>` yields `Pair<CharSequence, Number>`
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.OutTypeArgumentsSupertypes)
 */
@OptIn(InternalAPI::class)
public val OutTypeArgumentsSupertypes: DependencyKeyCovariance =
    DependencyKeyCovariance { key, distance ->
        var distance = distance
        key.type.typeParametersHierarchy().map {
            DependencyKey(it, key.name) to distance++
        }
    }

/**
 * Helper operator function for combining covariance logic.
 *
 * Returns a composed function that results in the cartesian product of the two outputs.
 *
 * For example, to match all supertypes, named or unnamed, including the input key:
 * ```
 * Supertypes * Unnamed
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.times)
 */
public operator fun DependencyKeyCovariance.times(other: DependencyKeyCovariance): DependencyKeyCovariance =
    DependencyKeyCovariance { key, distance ->
        this@times.map(key, distance).flatMap { match ->
            other.map(match.key, match.distance)
        }
    }

/**
 * Helper operator function for combining covariance logic.
 *
 * Returns a composed function that results in the union of the two outputs.
 *
 * For example, to match only the unnamed key of the input, and the named supertypes:
 * ```
 * Supertypes + Unnamed
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.plus)
 */
public operator fun DependencyKeyCovariance.plus(other: DependencyKeyCovariance): DependencyKeyCovariance =
    DependencyKeyCovariance { key, distance ->
        sequence {
            yieldAll(this@plus.map(key, distance))
            yieldAll(other.map(key, distance))
        }
    }

/**
 * The default covariance logic for dependency keys.
 *
 * Where applicable, this supports super type, nullable, and type argument supertypes.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DefaultKeyCovariance)
 */
public val DefaultKeyCovariance: DependencyKeyCovariance =
    Supertypes * Nullables * OutTypeArgumentsSupertypes
