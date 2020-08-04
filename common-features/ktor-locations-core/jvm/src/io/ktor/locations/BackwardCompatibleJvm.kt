/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

internal actual fun createImpl(
    conversionServiceProvider: () -> ConversionService?,
    routeService: LocationRouteService,
    logger: (String) -> Unit,
    module: SerializersModule,
    compatibilityMode: Boolean
): LocationsImpl = when (compatibilityMode) {
    true -> BackwardCompatibleImpl(
        { conversionServiceProvider() ?: error("ConversionService is required") },
        routeService,
        logger
    )
    false -> SerializationImpl(module, conversionServiceProvider, routeService, logger)
}

internal actual fun backwardCompatibleParentClass(locationClass: KClass<*>): KClass<*>? {
    return locationClass.java.declaringClass?.kotlin
}

internal actual fun propertyType(locationClass: KClass<*>?, propertyName: String): KClass<*>? {
    val property = locationClass?.memberProperties?.first { it.name == propertyName } ?: return null
    return property.returnType.jvmErasure
}

/**
 * Guess a KClass by SerialDescriptor, JVM only
 */
@InternalAPI
public actual fun SerialDescriptor.guessKClass(): KClass<*>? {
    try {
        return Class.forName(serialName).kotlin
    } catch (_: NoClassDefFoundError) {
    } catch (_: ClassNotFoundException) {
    }

    var index = serialName.lastIndex
    while (index > 0) {
        val dot = serialName.lastIndexOf('.', index)
        if (dot == -1) break

        val clazz = try {
            Class.forName(serialName.substring(0, dot)).kotlin
        } catch (_: NoClassDefFoundError) {
            null
        } catch (_: ClassNotFoundException) {
            null
        }

        if (clazz != null) {
            val remaining = serialName.substring(dot + 1).split('.')
            var locationClass: KClass<*> = clazz
            remaining.forEach { part ->
                locationClass = locationClass.nestedClasses.first { it.simpleName == part }
            }

            return locationClass
        }

        index = dot - 1
    }

    throw UnsupportedOperationException("@SerialName is not supported in Locations yet.")
}

internal actual fun KClass<*>.annotationsList(): List<Annotation>? = annotations
