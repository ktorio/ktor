/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import io.ktor.server.application.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

internal fun executeModuleFunction(
    classLoader: ClassLoader,
    fqName: String,
    application: HttpServer
) {
    val name = fqName.lastIndexOfAny(".#".toCharArray())

    if (name == -1) {
        throw ReloadingException("Module function cannot be found for the fully qualified name '$fqName'")
    }

    val className = fqName.substring(0, name)
    val moduleName = fqName.substring(name + 1)
    val clazz = classLoader.loadClassOrNull(className)
        ?: throw ReloadingException("Module function cannot be found for the fully qualified name '$fqName'")

    // Get from static function like `fun Application.module()`
    clazz.methods
        .filter { it.name == moduleName && Modifier.isStatic(it.modifiers) }
        .mapNotNull { it.kotlinFunction }
        .filter { it.isApplicableFunction() }
        .bestFunction()
        ?.let { moduleFunction ->
            if (moduleFunction.parameters.none { it.kind == KParameter.Kind.INSTANCE }) {
                callFunctionWithInjection(null, moduleFunction, application)
                return
            }
    }

    // Get from lambda property like `val module: ServerModule = {}`
    clazz.methods
        .filter { it.name == moduleName.getterName && Modifier.isStatic(it.modifiers) }
        .firstNotNullOfOrNull {
            runCatching {
                val module = it.invoke(null) as? ServerModule
                module?.invoke(application)
            }.getOrNull()
        }
        ?.let {
            return
        }

    // Get from function type `class Module: Function1`
    try {
        if (Function1::class.java.isAssignableFrom(clazz)) {
            val constructor = clazz.declaredConstructors.single()
            if (constructor.parameterCount != 0) {
                throw ReloadingException("Module function with captured variables cannot be instantiated '$fqName'")
            }

            constructor.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val function = constructor.newInstance() as Function1<HttpServer, Unit>
            function(application)
            return
        }
    } catch (_: NoSuchMethodError) {
        // Skip this case for the Android device
    }

    // Get from a member function of a class
    val kclass = clazz.takeIfNotFacade()
        ?: throw ReloadingException("Module function cannot be found for the fully qualified name '$fqName'")

    kclass.functions
        .filter { it.name == moduleName && it.isApplicableFunction() }
        .bestFunction()?.let { moduleFunction ->
            val instance = createModuleContainer(kclass, application)
            callFunctionWithInjection(instance, moduleFunction, application)
            return
        }


    throw ClassNotFoundException("Module function cannot be found for the fully qualified name '$fqName'")
}

private fun createModuleContainer(
    applicationEntryClass: KClass<*>,
    application: HttpServer
): Any {
    val objectInstance = applicationEntryClass.objectInstance
    if (objectInstance != null) return objectInstance

    val constructors = applicationEntryClass.constructors.filter {
        it.parameters.all { p -> p.isOptional || isApplicationEnvironment(p) || isApplication(p) }
    }

    val constructor = constructors.bestFunction()
        ?: throw RuntimeException("There are no applicable constructors found in class $applicationEntryClass")

    return callFunctionWithInjection(null, constructor, application)
}

private val String.getterName: String get() =
    "get${get(0).uppercase()}${substring(1)}"

private fun <R> callFunctionWithInjection(
    instance: Any?,
    entryPoint: KFunction<R>,
    application: HttpServer
): R {
    val args = entryPoint.parameters.filterNot { it.isOptional }.associateBy(
        { it },
        { parameter ->
            when {
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

                else -> throw IllegalArgumentException(
                    "Parameter type '${parameter.type}' of parameter " +
                        "'${parameter.name ?: "<receiver>"}' is not supported"
                )
            }
        }
    )

    try {
        return entryPoint.callBy(args)
    } catch (cause: InvocationTargetException) {
        throw cause.cause ?: cause
    }
}

internal class ReloadingException(message: String) : RuntimeException(message)
