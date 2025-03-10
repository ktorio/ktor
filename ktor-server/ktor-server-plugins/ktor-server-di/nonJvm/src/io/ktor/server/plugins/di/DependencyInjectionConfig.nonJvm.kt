/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import kotlin.reflect.KClass

public actual val DefaultReflection: DependencyReflection
    get() = ReflectionUnavailable

internal object ReflectionUnavailable : DependencyReflection {
    override fun <T : Any> create(
        kClass: KClass<T>,
        init: (DependencyKey) -> Any
    ): T = TODO("Reflection is currently unavailable for non-JVM targets")
}
