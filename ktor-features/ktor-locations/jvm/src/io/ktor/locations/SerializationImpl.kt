/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

internal class SerializationImpl(
    private val module: SerialModule,
    application: Application,
    routeService: LocationRouteService
) : LocationsImpl(application, routeService) {
    init {
        check(routeService is LocationAttributeRouteService) {
            "Only the default route service works with kotlinx.serialization"
        }
    }

    private val cache = HashMap<SerialDescriptor, LocationInfo>()

    @OptIn(ImplicitReflectionSerializer::class)
    override fun createInfo(locationClass: KClass<*>): LocationInfo = info.getOrPut(locationClass) {
        createInfo(locationClass.serializer().descriptor, locationClass)
    }

    @OptIn(ImplicitReflectionSerializer::class)
    private fun createInfo(
        locationDescriptor: SerialDescriptor,
        locationClass: KClass<*>
    ): LocationInfo = cache.getOrPut(locationDescriptor) {
        val elements = locationDescriptor.elementDescriptors()
        val names = locationDescriptor.elementNames()
        val parentIndex = elements.indexOfFirst {
            it.kind.isClassOrObject() && it.location != null
        }

        val parameters = names.mapIndexed { index, name ->
            LocationPropertyInfoImplSerialization(name, locationDescriptor.isElementOptional(index))
        }

        val parentParameter = parentIndex.takeIf { it != -1 }?.let { parameters[it] }
        val path = locationDescriptor.location!!.path

        val pathParameterNames = RoutingPath.parse(path).parts
            .filter { it.kind == RoutingPathSegmentKind.Parameter }
            .map { PathSegmentSelectorBuilder.parseName(it.value) }

        val parent = parentParameter?.let { property ->
            val parentClass = locationClass.memberProperties
                .first { it.name == property.name }
                .returnType.jvmErasure

            createInfo(elements[parentIndex], parentClass)
        } ?: locationClass.java.declaringClass?.let { parentClass ->
            routeService.findRoute(parentClass.kotlin)?.let {
                createInfo(parentClass.kotlin)
            }
        }

        if (parent != null && parentParameter == null) {
            checkInfo(application, locationClass, parent)
        }

        return LocationInfo(
            locationClass,
            parent,
            parentParameter,
            path,
            pathParameterNames.mapNotNull { name -> parameters.firstOrNull { it.name == name } },
            parameters.filter { it.name !in pathParameterNames && it !== parentParameter },
            locationDescriptor
        )
    }

    @OptIn(ImplicitReflectionSerializer::class)
    override fun instantiate(info: LocationInfo, allParameters: Parameters): Any {
        val decoder = URLDecoder(module, null, allParameters, info.classRef)
        val serializer = info.classRef.serializer()
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

    @OptIn(ImplicitReflectionSerializer::class)
    override fun href(location: Any, builder: URLBuilder) {
        val encoder = URLEncoder(module, location.javaClass.kotlin)
        val serializer = location.javaClass.kotlin.serializer()
        serializer.serialize(encoder, location)
        encoder.buildTo(builder)
    }
}

private class LocationPropertyInfoImplSerialization(
    name: String,
    isOptional: Boolean
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
