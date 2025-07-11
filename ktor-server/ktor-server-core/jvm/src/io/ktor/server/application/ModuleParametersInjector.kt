/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import kotlin.reflect.KParameter

/**
 * Provides values for application module parameters.
 *
 * For example, the following module will trigger calls to resolve the parameters `param1` and `param2`:
 * ```
 * fun Application.module(param1: String, param2: List<Int>) {
 *     // contents
 * }
 * ```
 *
 * By default, parameters like `ApplicationEnvironment` are resolved automatically.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ModuleParametersInjector)
 */
public fun interface ModuleParametersInjector {
    public companion object {
        internal val Disabled: ModuleParametersInjector = ModuleParametersInjector { _, _ ->
            throw IllegalArgumentException("No module injector configured")
        }
    }

    /**
     * Given the [Application] instance as context, resolves the expected value of the provided [KParameter].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ModuleParametersInjector.resolveParameter)
     */
    public suspend fun resolveParameter(
        application: Application,
        parameter: KParameter
    ): Any?
}
