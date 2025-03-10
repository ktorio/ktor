/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.util.reflect.TypeInfo
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

/**
 * Provides the default reflection behavior for the JVM platform, relying on the standard library
 * calls for inferring which constructors to use, and how to evaluate the parameters as dependency keys.
 */
public open class DependencyReflectionJvm : DependencyReflection {
    override fun <T : Any> create(kClass: KClass<T>, init: (DependencyKey) -> Any): T =
        findConstructors(kClass).firstNotNullOf { constructor ->
            runCatching {
                constructor.callBy(
                    constructor.parameters.associateWith { parameter ->
                        init(toDependencyKey(parameter))
                    }
                )
            }.getOrNull()
        }

    public open fun <T : Any> findConstructors(kClass: KClass<T>): Collection<KFunction<T>> =
        kClass.constructors

    public open fun toDependencyKey(parameter: KParameter): DependencyKey =
        DependencyKey(TypeInfo(parameter.type.jvmErasure, parameter.type))
}
