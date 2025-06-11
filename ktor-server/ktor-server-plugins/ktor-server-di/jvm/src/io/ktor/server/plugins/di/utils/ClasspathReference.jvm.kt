/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.util.reflect.*
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

internal actual fun Application.installReference(
    registry: DependencyRegistry,
    reference: ClasspathReference,
) {
    val reflection = registry.reflection as? DependencyReflectionJvm ?: DependencyReflectionJvm()
    val classLoader = Thread.currentThread().contextClassLoader
    val clazz = classLoader.load(reference)

    if (clazz.name == reference.value) {
        // If this is a class reference,
        // treat it as a `create(Object::class)` call
        val kotlinType = clazz.kotlin
        val classTypeInfo = TypeInfo(kotlinType, kotlinType.starProjectedType)

        registry.set(DependencyKey(classTypeInfo)) {
            reflection.create(kotlinType, ::get)
        }
    } else {
        // Else, we assume this is a function reference
        // where parameters are supplied by the DI container
        // and the return type is used as the declaration
        val matchingMethods = clazz.declaredMethods.filter { it.name == reference.name }
        val functionCandidates = matchingMethods.mapNotNull { it.kotlinFunction }.takeIf { it.isNotEmpty() }
            ?: throw InvalidDependencyReferenceException("Missing function reference", reference)
        val returnType = functionCandidates.map { it.returnType }.distinct().singleOrNull()
            ?: throw InvalidDependencyReferenceException("Ambiguous return types", reference)
        val returnTypeInfo = TypeInfo(returnType.jvmErasure, returnType)

        registry.set(DependencyKey(returnTypeInfo)) {
            var lastError: Throwable? = null
            for (function in functionCandidates) {
                try {
                    return@set function.callSuspendBy(
                        reflection.mapParameters(function.parameters) { param ->
                            when (param.type) {
                                // special types, from application
                                DependencyRegistry::class.starProjectedType -> registry
                                DependencyMap::class.starProjectedType,
                                DependencyResolver::class.starProjectedType -> this@set
                                Application::class.starProjectedType -> this@installReference
                                ApplicationEnvironment::class.starProjectedType -> environment
                                ApplicationConfig::class.starProjectedType -> environment.config
                                // regular types, from resolver
                                else -> get(reflection.toDependencyKey(param))
                            }
                        }
                    )
                } catch (cause: Throwable) {
                    lastError = cause
                }
            }
            throw DependencyInjectionException("Failed to invoke classpath reference $reference", lastError!!)
        }
    }
}

private fun ClassLoader.load(reference: ClasspathReference): Class<*> =
    try {
        loadClass(reference.value)
    } catch (cause: Exception) {
        runCatching {
            loadClass(reference.container)
        }.getOrElse {
            throw InvalidDependencyReferenceException("Reference not found", reference, cause)
        }
    }
