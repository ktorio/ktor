/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*

internal expect fun createImpl(
    conversionServiceProvider: () -> ConversionService?,
    routeService: LocationRouteService,
    logger: (String) -> Unit,
    module: SerializersModule,
    compatibilityMode: Boolean
): LocationsImpl

internal expect fun backwardCompatibleParentClass(locationClass: KClass<*>): KClass<*>?

@OptIn(UnsafeSerializationApi::class)
internal fun backwardCompatibleParent(locationClass: KClass<*>): LocationPattern? {
    val parentClass = backwardCompatibleParentClass(locationClass) ?: return null
    val descriptor = parentClass.serializerOrNull()?.descriptor ?: return null

    return buildLocationPattern(descriptor, parentClass)
}

internal expect fun propertyType(locationClass: KClass<*>?, propertyName: String): KClass<*>?

internal expect fun KClass<*>.annotationsList(): List<Annotation>?

/**
 * Guess a KClass by SerialDescriptor, JVM only
 */
@InternalAPI
public expect fun SerialDescriptor.guessKClass(): KClass<*>?

internal fun buildLocationPattern(desc: SerialDescriptor, locationClass: KClass<*>?): LocationPattern {
    require(desc.kind.isClassOrObject())

    val children = desc.elementDescriptors.mapIndexedNotNull { index, child ->
        val name = desc.getElementName(index)
        val type = propertyType(locationClass, name)

        child.location?.let { buildLocationPattern(child, type) }
    }

    val thisPattern = when (val location = desc.location) {
        null -> buildDummyPattern(desc)
        else -> LocationPattern(location.path)
    }

    return when (children.size) {
        0 -> locationClass?.let { backwardCompatibleParent(locationClass) }?.let { parent -> parent + thisPattern } ?: thisPattern
        1 -> children[0] + thisPattern
        else -> error("Multiple parents with @Location annotations")
    }
}

private fun buildDummyPattern(desc: SerialDescriptor): LocationPattern {
    return LocationPattern("/" + desc.serialName.substringAfterLast("."))
}

internal val SerialDescriptor.location: Location?
    get() = annotations.singleOrNull { it is Location } as? Location
        ?: locationIfObject()

private fun SerialDescriptor.locationIfObject(): Location? = when (kind) {
    StructureKind.OBJECT -> guessKClass()?.annotationsList()?.firstOrNull { it is Location } as? Location
    else -> null
}

internal fun SerialKind.isClassOrObject(): Boolean =
    this == StructureKind.CLASS || this == StructureKind.OBJECT
