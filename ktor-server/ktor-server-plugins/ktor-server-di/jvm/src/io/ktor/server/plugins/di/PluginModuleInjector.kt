/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationModuleInjector
import kotlin.getValue
import kotlin.reflect.KParameter

public class PluginModuleInjector : ApplicationModuleInjector {
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
