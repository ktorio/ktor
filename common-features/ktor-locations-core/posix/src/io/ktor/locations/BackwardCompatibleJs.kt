/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*

internal actual fun createImpl(
    conversionServiceProvider: () -> ConversionService?,
    routeService: LocationRouteService,
    logger: (String) -> Unit,
    module: SerializersModule,
    compatibilityMode: Boolean
): LocationsImpl = SerializationImpl(module, conversionServiceProvider, routeService, logger)

internal actual fun backwardCompatibleParentClass(locationClass: KClass<*>): KClass<*>? = null

internal actual fun propertyType(locationClass: KClass<*>?, propertyName: String): KClass<*>? = null

/**
 * Guess a KClass by SerialDescriptor, JVM only
 */
public actual fun SerialDescriptor.guessKClass(): KClass<*>? {
    return null
}

@OptIn(UnsafeSerializationApi::class)
internal actual fun KClass<*>.annotationsList(): List<Annotation>? = serializerOrNull()?.descriptor?.annotations
