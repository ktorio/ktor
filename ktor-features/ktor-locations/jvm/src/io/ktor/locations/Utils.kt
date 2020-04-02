/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import kotlinx.serialization.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

internal fun buildLocationPattern(desc: SerialDescriptor, locationClass: KClass<*>): LocationPattern {
    require(desc.kind.isClassOrObject())

    val children = desc.elementDescriptors().mapIndexedNotNull { index, child ->
        val name = desc.getElementName(index)
        val property = locationClass.memberProperties.first { it.name == name }

        child.location?.let { buildLocationPattern(child, property.returnType.jvmErasure) }
    }

    val thisPattern = when (val location = desc.location) {
        null -> buildDummyPattern(desc)
        else -> LocationPattern(location.path)
    }

    return when (children.size) {
        0 -> backwardCompatibleParent(locationClass)?.let { parent -> parent + thisPattern } ?: thisPattern
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
    StructureKind.OBJECT -> toKClass().annotations.firstOrNull { it is Location } as? Location
    else -> null
}

internal fun SerialDescriptor.toKClass(): KClass<*> {
    try {
        return Class.forName(serialName).kotlin
    } catch(_: NoClassDefFoundError) {
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

@OptIn(ImplicitReflectionSerializer::class)
private fun backwardCompatibleParent(locationClass: KClass<*>): LocationPattern? {
    val parentClass = locationClass.java.declaringClass?.kotlin ?: return null
    val descriptor = parentClass.serializerOrNull()?.descriptor ?: return null

    return buildLocationPattern(descriptor, parentClass)
}

internal fun SerialKind.isClassOrObject(): Boolean =
    this == StructureKind.CLASS || this == StructureKind.OBJECT
