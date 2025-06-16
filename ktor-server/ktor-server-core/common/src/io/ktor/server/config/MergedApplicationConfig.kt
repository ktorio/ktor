/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.server.config.ApplicationConfigValue.Type.*
import io.ktor.server.config.MapApplicationConfig.Companion.flatten

/**
 * Merge configuration combining all their keys.
 * If key is not found in one of the configs, search will continue in the next config in the list.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.merge)
 */
@Deprecated(
    "Use mergeWith/withFallback instead.",
    level = DeprecationLevel.ERROR
)
public fun List<ApplicationConfig>.merge(): ApplicationConfig {
    require(isNotEmpty()) { "List of configs can not be empty" }
    return foldRight(last()) { config, acc -> config.withFallback(acc) }
}

/**
 * Merge configuration combining all their keys.
 * If the key exists in this and [other] config, the value from the [other] config will be used.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.mergeWith)
 *
 * @see [withFallback]
 */
public fun ApplicationConfig.mergeWith(other: ApplicationConfig): ApplicationConfig =
    when {
        keys().isEmpty() -> other
        other.keys().isEmpty() -> this
        else -> MergedApplicationConfig(other, this)
    }

/**
 * Merge configuration combining all their keys.
 * If the key exists in this and [other] config, the value from this config will be used.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.withFallback)
 *
 * @see [mergeWith]
 */
public fun ApplicationConfig.withFallback(other: ApplicationConfig): ApplicationConfig {
    return MergedApplicationConfig(this, other)
}

internal class MergedApplicationConfig(
    val first: ApplicationConfig,
    val second: ApplicationConfig
) : ApplicationConfig {

    private val firstKeys by lazy { first.keys() }
    private val secondKeys by lazy { second.keys() }

    override fun property(path: String): ApplicationConfigValue =
        merge(first.propertyOrNull(path), second.propertyOrNull(path)) ?: second.property(path)

    override fun propertyOrNull(path: String): ApplicationConfigValue? =
        merge(first.propertyOrNull(path), second.propertyOrNull(path))

    override fun config(path: String): ApplicationConfig {
        if (firstKeys.none { it.startsWith("$path.") }) return second.config(path)
        if (secondKeys.none { it.startsWith("$path.") }) return first.config(path)
        return MergedApplicationConfig(first.config(path), second.config(path))
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val firstList = if (firstKeys.contains(path)) first.configList(path) else emptyList()
        val secondList = if (secondKeys.contains(path)) second.configList(path) else emptyList()
        return firstList + secondList
    }

    override fun keys(): Set<String> = firstKeys + secondKeys

    override fun toMap(): Map<String, Any?> = second.toMap().merge(first.toMap())

    /**
     * Adds entries from `other` into `this` as a new map.
     *
     * Preference for keys is given to `other`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.merge(other: Map<String, Any?>): Map<String, Any?> =
        (this.keys + other.keys).associateWith { key ->
            val value1 = this[key]
            val value2 = other[key]

            when {
                value1 is Map<*, *> && value2 is Map<*, *> -> {
                    (value1 as Map<String, Any?>).merge(value2 as Map<String, Any?>)
                }

                else -> value2 ?: value1
            }
        }

    /**
     * Returns a non-null value with preference of the first, or null if both are null.
     *
     * When both are not null and objects, merge the keys from both.
     */
    private fun merge(
        first: ApplicationConfigValue?,
        second: ApplicationConfigValue?,
    ): ApplicationConfigValue? {
        val value = when {
            first == null -> second
            second == null -> first
            first.type != OBJECT || second.type != OBJECT -> first
            else -> mergeMapConfigValues(first, second)
        }
        return value
    }

    /**
     * Converts both config values to maps, then merges them with a preference for the first's keys.
     *
     * The resulting map is used to populate a `MapApplicationConfigValue` using a flattened copy.
     */
    private fun mergeMapConfigValues(
        first: ApplicationConfigValue,
        second: ApplicationConfigValue,
    ): ApplicationConfigValue {
        val firstMap = first.getMap()
        val secondMap = second.getMap()
        val mergedMap = secondMap.merge(firstMap)
        val flattenedMap = mergedMap.flatten().toMap().toMutableMap()

        return MapApplicationConfigValue(flattenedMap, "")
    }
}
