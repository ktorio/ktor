/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import io.ktor.server.application.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

internal fun executeModuleFunction(
    classLoader: ClassLoader,
    fqName: String,
    application: Application,
    moduleInjector: ApplicationModuleInjector
) {
    val name = fqName.lastIndexOfAny(".#".toCharArray())

    if (name == -1) {
        throw ReloadingException("Module function cannot be found for the fully qualified name '$fqName'")
    }

    val className = fqName.substring(0, name)
    val functionName = fqName.substring(name + 1)
    val clazz = classLoader.loadClassOrNull(className)
        ?: throw ReloadingException("Module function cannot be found for the fully qualified name '$fqName'")

    val staticFunctions = clazz.methods
        .filter { it.name == functionName && Modifier.isStatic(it.modifiers) }
        .mapNotNull { it.kotlinFunction }
        .filter { it.isApplicableFunction() }

    staticFunctions.bestFunction()?.let { moduleFunction ->
        if (moduleFunction.parameters.none { it.kind == KParameter.Kind.INSTANCE }) {
            callFunctionWithInjection(null, moduleFunction, application, moduleInjector)
            return
        }
    }

    try {
        if (Function1::class.java.isAssignableFrom(clazz)) {
            // Normally lambda has a single constructor, but this could change after R8/ProGuard optimizations
            val constructors = clazz.declaredConstructors
            if (constructors.isEmpty()) {
                throw ReloadingException("Module function cannot be instantiated '$fqName'")
            }
            val constructor = constructors.find { it.parameterCount == 0 }
                ?: throw ReloadingException("Module function with captured variables cannot be instantiated '$fqName'")

            constructor.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val function = constructor.newInstance() as Function1<Application, Unit>
            function(application)
            return
        }
    } catch (_: NoSuchMethodError) {
        // Skip this case for the Android device
    }

    val kclass = clazz.takeIfNotFacade()
        ?: throw ReloadingException("Module function cannot be found for the fully qualified name '$fqName'")

    kclass.functions
        .filter { it.name == functionName && it.isApplicableFunction() }
        .bestFunction()?.let { moduleFunction ->
            val instance = createModuleContainer(kclass, application, moduleInjector)
            callFunctionWithInjection(instance, moduleFunction, application, moduleInjector)
            return
        }

    throw ClassNotFoundException("Module function cannot be found for the fully qualified name '$fqName'")
}

private fun createModuleContainer(
    applicationEntryClass: KClass<*>,
    application: Application,
    moduleInjector: ApplicationModuleInjector
): Any {
    val objectInstance = applicationEntryClass.objectInstance
    if (objectInstance != null) return objectInstance

    val constructors = applicationEntryClass.constructors.filter {
        it.parameters.all { p -> p.isOptional || isApplicationEnvironment(p) || isApplication(p) }
    }

    val constructor = constructors.bestFunction()
        ?: throw RuntimeException("There are no applicable constructors found in class $applicationEntryClass")

    return callFunctionWithInjection(null, constructor, application, moduleInjector)
}

private fun <R> callFunctionWithInjection(
    instance: Any?,
    entryPoint: KFunction<R>,
    application: Application,
    moduleInjector: ApplicationModuleInjector
): R {
    val args = entryPoint.parameters.mapNotNull { parameter ->
        parameter to when {
            parameter.kind == KParameter.Kind.INSTANCE -> instance
            isApplicationEnvironment(parameter) -> application.environment
            isApplication(parameter) -> application
            parameter.type.toString().contains("Application") -> {
                // It is possible that type is okay, but classloader is not
                val classLoader = (parameter.type.javaType as? Class<*>)?.classLoader
                throw IllegalArgumentException(
                    "Parameter type ${parameter.type}:{$classLoader} is not supported." +
                        "Application is loaded as " +
                        "$ApplicationClassInstance:{${ApplicationClassInstance.classLoader}}"
                )
            }
            else -> {
                val injectedValue = runCatching {
                    moduleInjector.resolveParameter(application, parameter)
                }
                when {
                    injectedValue.isSuccess -> injectedValue.getOrThrow()
                    parameter.isOptional -> return@mapNotNull null // skip
                    parameter.type.isMarkedNullable -> null // value = null
                    else -> throw IllegalArgumentException(
                        "Failed to inject parameter `${parameter.name ?: "<receiver>"}: ${parameter.type}` " +
                            "in module function `$entryPoint`",
                        injectedValue.exceptionOrNull()
                    )
                }
            }
        }
    }.toMap()

    try {
        return entryPoint.callBy(args)
    } catch (cause: InvocationTargetException) {
        throw cause.cause ?: cause
    }
}

internal class ReloadingException(message: String) : RuntimeException(message)
