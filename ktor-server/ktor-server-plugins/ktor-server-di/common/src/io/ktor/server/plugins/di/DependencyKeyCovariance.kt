/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.utils.*
import io.ktor.util.reflect.TypeInfo
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
 * A [DependencyKeyCovariance] that generates raw type variants from parameterized types.
 *
 * For example, `Pair<String, Int>` yields `Pair`
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.RawTypes)
 */
@OptIn(InternalAPI::class)
public val RawTypes: DependencyKeyCovariance =
    DependencyKeyCovariance { key, distance ->
        sequence {
            yield(key to distance)
            if (key.type.kotlinType != null) {
                yield(key.copy(type = key.type.toRawType()) to distance.inc())
            }
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
    Supertypes * Nullables * OutTypeArgumentsSupertypes * RawTypes

/**
 * Parses expressions using the OOTB key mapping rules.
 *
 * See [DependencyKeyCovarianceParser.mappings] for the list of supported OOTB options.
 *
 * When using expressions, operators are supported, including `+`, `*`, and `()`.
 *
 * Examples:
 * - `Supertypes * Nullables` will provide all supertypes and nullable versions of those supertypes.
 * - `Supertypes + (Nullables + Unnamed)` will provide the same, but also unnamed versions of the supertypes.
 *    It will not include nullable, unnamed versions of the supertypes, however.
 */
internal fun parseKeyMapping(text: String): DependencyKeyCovariance {
    return DependencyKeyCovarianceParser(text).parse()
}

private class DependencyKeyCovarianceParser(
    private val text: String,
) {
    companion object {
        private val mappings = mapOf(
            "Supertypes" to Supertypes,
            "SuperTypes" to Supertypes,
            "Unnamed" to Unnamed,
            "Nullables" to Nullables,
            "OutTypeArgumentsSupertypes" to OutTypeArgumentsSupertypes,
            "RawTypes" to RawTypes,
            "Default" to DefaultKeyCovariance
        )
    }
    private var position = 0

    fun parse(): DependencyKeyCovariance {
        skipWhitespace()
        val result = parseAddition()
        skipWhitespace()
        check(position >= text.length) {
            "Unexpected character at position $position: '${text[position]}'"
        }
        return result
    }

    private fun parseAddition(): DependencyKeyCovariance {
        var left = parseMultiplication()

        while (true) {
            skipWhitespace()
            if (position >= text.length || peek() != '+') break

            consume('+')
            skipWhitespace()
            val right = parseMultiplication()
            left += right
        }

        return left
    }

    private fun parseMultiplication(): DependencyKeyCovariance {
        var left = parsePrimary()

        while (true) {
            skipWhitespace()
            if (position >= text.length || peek() != '*') break

            consume('*')
            skipWhitespace()
            val right = parsePrimary()
            left *= right
        }

        return left
    }

    private fun parsePrimary(): DependencyKeyCovariance {
        skipWhitespace()

        return when {
            position >= text.length -> error("Unexpected end of expression")
            peek() == '(' -> {
                consume('(')
                val result = parseAddition()
                skipWhitespace()
                consume(')')
                result
            }
            else -> parseIdentifier()
        }
    }

    private fun parseIdentifier(): DependencyKeyCovariance {
        val start = position
        while (position < text.length && (text[position].isLetterOrDigit() || text[position] == '_')) {
            position++
        }

        check(start < position) {
            "Expected identifier at position $position"
        }

        val identifier = text.substring(start, position)
        return mappings[identifier]
            ?: error("Unknown mapping: '$identifier'. Available: ${mappings.keys.joinToString(", ")}")
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position].isWhitespace()) {
            position++
        }
    }

    private fun peek(): Char = text[position]

    private fun consume(expected: Char) {
        check(position < text.length) {
            "Expected '$expected' but reached end of expression"
        }
        check(text[position] == expected) {
            "Expected '$expected' but found '${text[position]}' at position $position"
        }
        position++
    }
}
