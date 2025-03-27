/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.Application
import io.ktor.server.application.ModuleParametersInjector
import kotlin.getValue
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
    override fun resolveParameter(
        application: Application,
        parameter: KParameter
    ): Any? {
        val reflection = application.dependencies.reflection as? DependencyReflectionJvm
            ?: DependencyReflectionJvm()
        val parameterValue by lazy<Any> {
            application.dependencies.get(reflection.toDependencyKey(parameter))
        }
        return parameterValue
    }
}
