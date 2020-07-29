/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*

internal class SerializationImpl(
    private val initialModule: SerialModule,
    private val conversionServiceProvider: () -> ConversionService?,
    routeService: LocationRouteService,
    private val logger: (String) -> Unit
) : LocationsImpl(routeService) {
    init {
        check(routeService is LocationAttributeRouteService) {
            "Only the default route service works with kotlinx.serialization"
        }
    }

    private val cache = HashMap<SerialDescriptor, LocationInfo>()

    @OptIn(UnsafeSerializationApi::class)
    override fun createInfo(locationClass: KClass<*>): LocationInfo = info.getOrPut(locationClass) {
        createInfo(locationClass.serializer().descriptor, locationClass)
    }

    @OptIn(UnsafeSerializationApi::class)
    private fun createInfo(
        locationDescriptor: SerialDescriptor,
        locationClass: KClass<*>?
    ): LocationInfo = cache.getOrPut(locationDescriptor) {
        val elements = locationDescriptor.elementDescriptors()
        val names = locationDescriptor.elementNames()
        val parentIndex = elements.indexOfFirst {
            it.kind.isClassOrObject() && it.location != null
        }

        val parameters = names.mapIndexed { index, name ->
            LocationPropertyInfoImplSerialization(
                name,
                locationDescriptor.isElementOptional(index),
                locationDescriptor.getElementDescriptor(index)
            )
        }

        val parentParameter = parentIndex.takeIf { it != -1 }?.let { parameters[it] }
        val path = locationDescriptor.location!!.path

        val pathParameterNames = RoutingPath.parse(path).parts
            .filter { it.kind == RoutingPathSegmentKind.Parameter }
            .map { parseRoutingParameterName(it.value) }

        val parent = computeParent(parentParameter, locationClass)

        if (parent != null && parentParameter == null) {
            checkInfo(logger, locationDescriptor.toString(), parent)
        }

        // do not inline the variable: it is here to avoid resolution issue
        val type: KClass<*> = locationClass ?: Any::class
        return LocationInfo(
            type,
            parent,
            parentParameter,
            path,
            pathParameterNames.mapNotNull { name -> parameters.firstOrNull { it.name == name } },
            parameters.filter { it.name !in pathParameterNames && it !== parentParameter },
            locationDescriptor
        )
    }

    private fun computeParent(
        parentParameter: LocationPropertyInfoImplSerialization?,
        locationClass: KClass<*>?
    ): LocationInfo? {
        parentParameter?.let { property ->
            val type = propertyType(locationClass, property.name)
            createInfo(property.propertyDescriptor, type)
        }?.let { return it }

        if (locationClass == null) {
            return null
        }

        val backwardCompatibleParent: KClass<*>? =  backwardCompatibleParentClass(locationClass)

        return backwardCompatibleParent?.let { parentClass: KClass<*> ->
            routeService.findRoute(parentClass)?.let { _ ->
                createInfo(parentClass)
            }
        }
    }

    @OptIn(UnsafeSerializationApi::class)
    override fun <T : Any> instantiate(
        info: LocationInfo,
        allParameters: Parameters,
        klass: KClass<T>
    ): T {
        val decoder = URLDecoder(module, null, allParameters, info.klassOrNull)
        val serializer = klass.serializer()
        return try {
            serializer.deserialize(decoder)
        } catch (e: ParameterConversionException) {
            throw e
        } catch (e: Throwable) {
            throw DataConversionException("Failed to convert parameters to location")
        }
    }

    override fun href(instance: Any): String {
        val builder = URLBuilder()

        href(instance, builder)

        return builder.urlPathAndQuery()
    }

    @OptIn(UnsafeSerializationApi::class)
    override fun href(location: Any, builder: URLBuilder) {
        @Suppress("UNCHECKED_CAST")
        val clazz = location::class as KClass<Any>
        val encoder = URLEncoder(module, clazz)
        val serializer = clazz.serializer()
        serializer.serialize(encoder, location)
        encoder.buildTo(builder)
    }

    private val module: SerialModule
        get() {
            val conversionService = conversionServiceProvider() ?: return initialModule
            val types = conversionService.supportedTypes()
            if (types.isEmpty()) return initialModule

            return SerializersModule {
                include(initialModule)
                val visited = HashSet<KClass<*>>()
                types.forEach { type ->
                    val klass = type.classifier as KClass<*>
                    if (visited.add(klass)) {
                        contextual(klass, ConverterSerializer(type, conversionService))
                    }
                }
            }
        }
}

private class LocationPropertyInfoImplSerialization(
    name: String,
    isOptional: Boolean,
    internal val propertyDescriptor: SerialDescriptor
) : LocationPropertyInfo(name, isOptional)

/**
 * Append URL full path with query parameters. No host/port/auth/scheme will be appended.
 */
internal fun URLBuilder.urlPathAndQuery(): String = buildString {
    if (encodedPath.isNotBlank() && !encodedPath.startsWith("/")) {
        append('/')
    }

    append(encodedPath)

    if (!parameters.isEmpty()) {
        append("?")
        parameters.build().formUrlEncodeTo(this)
    }
}
