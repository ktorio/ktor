/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
import kotlin.reflect.KParameter

/**
 * Provides a mechanism for injecting application module parameters by resolving them
 * using the application's dependency reflection and dependency container.
 *
 * This implementation utilizes `DependencyReflectionJvm` for converting `KParameter`
 * instances into dependency keys, which are then used to fetch the corresponding
 * parameter values from the application's dependency container.
 */
internal class PluginModuleParametersInjector : ModuleParametersInjector {
    override suspend fun resolveParameter(
        application: Application,
        parameter: KParameter
    ): Any? {
        val dependencies = application.dependencies
        val reflection = dependencies.reflection as? DependencyReflectionJvm ?: DependencyReflectionJvm()
        val key = reflection.toDependencyKey(parameter).also(dependencies::require)
        return dependencies.get(key)
    }
}
