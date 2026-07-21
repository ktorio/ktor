/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.internal.GeneratedSerializer

internal actual fun typeName(mapping: JsonSchema.Discriminator.Mapping): String? =
    mapping.ref.qualifiedName ?: mapping.ref.simpleName

internal actual fun sealedSubclassComponentNameMapping(serializer: KSerializer<*>?): Map<String, String> {
    val sealedSerializer = serializer?.unwrapNullableSerializer() ?: return emptyMap()
    return sealedSubclassEntries(sealedSerializer)
        .mapNotNull { (subclass, subclassSerializer) ->
            val componentName = subclass.qualifiedName ?: subclass.simpleName ?: return@mapNotNull null
            subclassSerializer.descriptor.serialName to componentName
        }
        .toMap()
}

@OptIn(InternalSerializationApi::class)
internal actual fun nestedSerializerAt(serializer: KSerializer<*>?, index: Int): KSerializer<*>? {
    if (serializer == null) return null

    if (serializer.descriptor.isNullable) {
        val nestedSerializer = serializer.findFieldValue<KSerializer<*>>("serializer") ?: return null
        return nestedSerializerAt(nestedSerializer, index)
    }

    sealedSubclassEntries(serializer).getOrNull(index)?.second?.let { return it }

    if (serializer is GeneratedSerializer<*>) {
        return serializer.childSerializers().getOrNull(index)
    }

    val fieldNames = when {
        serializer.descriptor.kind == StructureKind.LIST -> listOf(
            "elementSerializer"
        )

        serializer.descriptor.kind == StructureKind.MAP && index == 0 ->
            listOf("keySerializer")

        serializer.descriptor.kind == StructureKind.MAP && index == 1 ->
            listOf("valueSerializer")

        else -> emptyList()
    }

    return fieldNames.firstNotNullOfOrNull { fieldName ->
        serializer.findFieldValue<KSerializer<*>>(fieldName)
    }
}

internal actual fun subclassComponentName(serializer: KSerializer<*>?): String? {
    if (serializer == null) return null

    serializer.javaClass.enclosingClass?.name?.let { return it.replace('$', '.') }

    val serializerClassName = serializer.javaClass.name
    if (serializerClassName.endsWith($$"$$serializer")) {
        return serializerClassName.removeSuffix($$"$$serializer").replace('$', '.')
    }

    return null
}

private fun KSerializer<*>.unwrapNullableSerializer(): KSerializer<*>? =
    if (descriptor.isNullable) findFieldValue("serializer") else this

private fun sealedSubclassEntries(serializer: KSerializer<*>): List<Pair<kotlin.reflect.KClass<*>, KSerializer<*>>> {
    @Suppress("UNCHECKED_CAST")
    val classToSerializer = when {
        serializer.javaClass.methods.any { it.name == $$"getClass2Serializer$kotlinx_serialization_core" } -> {
            val method = serializer.javaClass.methods
                .first { it.name == $$"getClass2Serializer$kotlinx_serialization_core" }
            method.invoke(serializer) as? Map<*, *>
        }

        else -> serializer.findFieldValue("class2Serializer")
    }

    classToSerializer?.mapNotNull { (subclass, subclassSerializer) ->
        val kClass = subclass.toKClassOrNull() ?: return@mapNotNull null
        val serializerValue = subclassSerializer as? KSerializer<*> ?: return@mapNotNull null
        kClass to serializerValue
    }?.let { return it }

    val subclasses = sealedSubclassKClasses(serializer) ?: return emptyList()
    val subclassSerializers = sealedSubclassSerializers(serializer) ?: return emptyList()
    return subclasses.zip(subclassSerializers)
}

private fun sealedSubclassKClasses(serializer: KSerializer<*>): List<kotlin.reflect.KClass<*>>? {
    val subclassesArray = serializer.findFieldValue<Array<*>>("subclasses")
    if (subclassesArray != null) {
        return subclassesArray.mapNotNull { it.toKClassOrNull() }
    }

    val subclassesList = serializer.findFieldValue<List<*>>("subclasses")
    if (subclassesList != null) {
        return subclassesList.mapNotNull { it.toKClassOrNull() }
    }

    return null
}

private fun sealedSubclassSerializers(serializer: KSerializer<*>): List<KSerializer<*>>? {
    val serializersArray = serializer.findFieldValue<Array<*>>("subclassSerializers")
    if (serializersArray != null) {
        return serializersArray.filterIsInstance<KSerializer<*>>()
    }

    val serializersList = serializer.findFieldValue<List<*>>("subclassSerializers")
    if (serializersList != null) {
        return serializersList.filterIsInstance<KSerializer<*>>()
    }

    return null
}

private fun <T> Any.findFieldValue(name: String): T? {
    var current: Class<*>? = javaClass
    while (current != null) {
        current.declaredFields.firstOrNull { it.name == name }?.let { field ->
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return field.get(this) as? T
        }
        current = current.superclass
    }
    return null
}

private fun Any?.toKClassOrNull(): kotlin.reflect.KClass<*>? = when (this) {
    is kotlin.reflect.KClass<*> -> this
    is Class<*> -> kotlin
    else -> null
}
