/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

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
public fun ApplicationConfig.mergeWith(other: ApplicationConfig): ApplicationConfig {
    return MergedApplicationConfig(other, this)
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

    override fun property(path: String): ApplicationConfigValue = when {
        firstKeys.contains(path) -> first.property(path)
        else -> second.property(path)
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? = when {
        firstKeys.contains(path) -> first.propertyOrNull(path)
        else -> second.propertyOrNull(path)
    }

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
}
