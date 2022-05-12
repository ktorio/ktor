/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

/**
 * Merge configuration combining all their keys.
 * If key is not found in one of the configs, search will continue in the next config in the list.
 */
public fun List<ApplicationConfig>.merge(): ApplicationConfig {
    require(isNotEmpty()) { "List of configs can not be empty" }
    return foldRight(last()) { config, acc -> MergedApplicationConfig(config, acc) }
}

internal class MergedApplicationConfig(
    val first: ApplicationConfig,
    val second: ApplicationConfig
) : ApplicationConfig {

    private val firstKeys by lazy { first.keys() }

    override fun property(path: String): ApplicationConfigValue = when {
        firstKeys.contains(path) -> first.property(path)
        else -> second.property(path)
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? = when {
        firstKeys.contains(path) -> first.propertyOrNull(path)
        else -> second.propertyOrNull(path)
    }

    override fun config(path: String): ApplicationConfig = when {
        firstKeys.contains(path) -> first.config(path)
        else -> second.config(path)
    }

    override fun configList(path: String): List<ApplicationConfig> = when {
        firstKeys.contains(path) -> first.configList(path)
        else -> second.configList(path)
    }

    override fun keys(): Set<String> = firstKeys + second.keys()
}
