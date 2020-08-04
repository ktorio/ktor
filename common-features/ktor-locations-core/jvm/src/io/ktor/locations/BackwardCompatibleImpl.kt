/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlin.collections.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

@OptIn(KtorExperimentalLocationsAPI::class)
internal class BackwardCompatibleImpl(
    private val conversionServiceProvider: () -> ConversionService,
    routeService: LocationRouteService,
    private val logger: (String) -> Unit
) : LocationsImpl(routeService) {
    private data class ResolvedUriInfo(val path: String, val query: List<Pair<String, String>>)

    private val rootUri = ResolvedUriInfo("", emptyList())

    override fun createInfo(locationClass: KClass<*>): LocationInfo {
        return getOrCreateInfo(locationClass, HashSet())
    }

    override fun <T : Any> instantiate(
        info: LocationInfo,
        allParameters: Parameters,
        klass: KClass<T>
    ): T {
        @Suppress("UNCHECKED_CAST")
        return klass.cast(info.create(allParameters))
    }

    override fun href(instance: Any): String {
        val info = pathAndQuery(instance)
        return info.path + if (info.query.any())
            "?" + info.query.formUrlEncode()
        else
            ""
    }

    override fun href(location: Any, builder: URLBuilder) {
        val info = pathAndQuery(location)
        builder.encodedPath = info.path
        for ((name, value) in info.query) {
            builder.parameters.append(name, value)
        }
    }

    private fun pathAndQuery(location: Any): ResolvedUriInfo {
        val info = getOrCreateInfo(location::class.java.kotlin)

        fun propertyValue(instance: Any, name: String): List<String> {
            // TODO: Cache properties by name in info
            val property = info.pathParameters.single { it.name == name }
            val value = property.getter(instance)
            return conversionServiceProvider().toValues(value)
        }

        val substituteParts = RoutingPath.parse(info.path).parts.flatMap {
            when (it.kind) {
                RoutingPathSegmentKind.Constant -> listOf(it.value)
                RoutingPathSegmentKind.Parameter -> {
                    if (info.klass.objectInstance != null)
                        throw IllegalArgumentException("There is no place to bind ${it.value} in object for '${info.serialDescriptor}'")
                    propertyValue(location, parseRoutingParameterName(it.value))
                }
            }
        }

        val relativePath = substituteParts
            .filterNot { it.isEmpty() }
            .joinToString("/") { it.encodeURLQueryComponent() }

        val parentInfo = when {
            info.parent == null -> rootUri
            info.parentParameter != null -> {
                val enclosingLocation = info.parentParameter.getter(location)!!
                pathAndQuery(enclosingLocation)
            }
            else -> ResolvedUriInfo(info.parent.path, emptyList())
        }

        val queryValues = info.queryParameters.flatMap { property ->
            val value = property.getter(location)
            conversionServiceProvider().toValues(value).map { property.name to it }
        }

        return parentInfo.combine(relativePath, queryValues)
    }

    private fun LocationInfo.create(allParameters: Parameters): Any {
        val objectInstance = klass.objectInstance
        if (objectInstance != null) return objectInstance

        val constructor: KFunction<Any> = klass.primaryConstructor ?: klass.constructors.single()
        val parameters = constructor.parameters
        val parent = parent
        val arguments = parameters.map { parameter ->
            val parameterType = parameter.type
            val parameterName = parameter.name ?: getParameterNameFromAnnotation(parameter)
            val value: Any? = if (parent != null && parameterType == parent.klass.starProjectedType) {
                parent.create(allParameters)
            } else {
                createFromParameters(allParameters, parameterName, parameterType, parameter.isOptional)
            }
            parameter to value
        }.filterNot { it.first.isOptional && it.second == null }.toMap()

        return constructor.callBy(arguments)
    }

    private fun createFromParameters(parameters: Parameters, name: String, type: KType, optional: Boolean): Any? {
        return when (val values = parameters.getAll(name)) {
            null -> when {
                !optional -> {
                    throw MissingParameterException(name, type.toString())
                }
                else -> null
            }
            else -> {
                try {
                    conversionServiceProvider().fromValues(values, type)
                } catch (cause: Throwable) {
                    throw ParameterConversionException(name, type.toString(), cause)
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun getParameterNameFromAnnotation(parameter: KParameter): String = TODO()

    private fun ResolvedUriInfo.combine(
        relativePath: String,
        queryValues: List<Pair<String, String>>
    ): ResolvedUriInfo {
        val pathElements = (path.split("/") + relativePath.split("/")).filterNot { it.isEmpty() }
        val combinedPath = pathElements.joinToString("/", "/")
        return ResolvedUriInfo(combinedPath, query + queryValues)
    }

    private fun getOrCreateInfo(
        locationClass: KClass<*>,
        visited: MutableSet<KClass<*>>
    ): LocationInfo {
        return info.getOrPut(locationClass) {
            check(visited.add(locationClass)) { "Cyclic dependencies in locations are not allowed." }

            val outerClass = locationClass.java.declaringClass?.kotlin
            val parentInfo = outerClass?.let {
                if (routeService.findRoute(outerClass) != null)
                    getOrCreateInfo(outerClass, visited)
                else
                    null
            }

            if (parentInfo != null && locationClass.isKotlinObject && parentInfo.isKotlinObject()) {
                logger(
                    "Object nesting in Ktor Locations is going to be deprecated. " +
                        "Convert nested object to a class with parameter. " +
                        "See https://github.com/ktorio/ktor/issues/1660 for more details."
                )
            }

            val path = routeService.findRoute(locationClass) ?: ""
            if (locationClass.objectInstance != null) {
                @OptIn(UnsafeSerializationApi::class)
                return@getOrPut LocationInfo(
                    locationClass,
                    parentInfo,
                    null, path, emptyList(), emptyList(),
                    locationClass.serializerOrNull()?.descriptor
                        ?: createSerialDescriptor(locationClass, emptyList())
                )
            }

            val constructor: KFunction<Any> =
                locationClass.primaryConstructor
                    ?: locationClass.constructors.singleOrNull()
                    ?: throw IllegalArgumentException("Class $locationClass cannot be instantiated because the constructor is missing")

            val declaredProperties = constructor.parameters.map { parameter ->
                val property =
                    locationClass.declaredMemberProperties.singleOrNull { property -> property.name == parameter.name }
                        ?: throw LocationRoutingException(
                            "Parameter ${parameter.name} of constructor " +
                                "for class ${locationClass.qualifiedName} should have corresponding property"
                        )

                @Suppress("UNCHECKED_CAST")
                LocationPropertyInfoImpl(
                    parameter.name ?: "<unnamed>",
                    (property as KProperty1<Any, Any?>).getter,
                    parameter.isOptional
                )
            }

            val parentParameter = declaredProperties.firstOrNull {
                it.kGetter.returnType == outerClass?.starProjectedType
            }

            if (parentInfo != null && parentParameter == null) {
                checkInfo(logger, locationClass.toString(), parentInfo)
            }

            val pathParameterNames = RoutingPath.parse(path).parts
                .filter { it.kind == RoutingPathSegmentKind.Parameter }
                .map { parseRoutingParameterName(it.value) }

            val declaredParameterNames = declaredProperties.map { it.name }.toSet()
            val invalidParameters = pathParameterNames.filter { it !in declaredParameterNames }
            if (invalidParameters.any()) {
                throw LocationRoutingException("Path parameters '$invalidParameters' are not bound to '$locationClass' properties")
            }

            val pathParameters = declaredProperties.filter { it.name in pathParameterNames }
            val queryParameters =
                declaredProperties.filterNot { pathParameterNames.contains(it.name) || it == parentParameter }

            @OptIn(UnsafeSerializationApi::class)
            LocationInfo(
                locationClass,
                parentInfo, parentParameter,
                path,
                pathParameters, queryParameters,
                locationClass.serializerOrNull()?.descriptor
                    ?: createSerialDescriptor(locationClass, constructor.parameters)
            )
        }
    }

    @KtorExperimentalLocationsAPI
    private val LocationPropertyInfo.getter: (Any) -> Any?
        get() = (this as LocationPropertyInfoImpl).kGetter

    private val KClass<*>.isKotlinObject: Boolean
        get() = isFinal && objectInstance != null
}

@OptIn(InternalSerializationApi::class)
private fun createSerialDescriptor(
    locationClass: KClass<*>,
    parameters: List<KParameter>
): SerialDescriptor {
    val kind = when {
        locationClass.objectInstance != null -> StructureKind.OBJECT
        else -> StructureKind.CLASS
    }
    val name = locationClass.qualifiedName ?: locationClass.jvmName

    return buildSerialDescriptor(name, kind) {
        annotations = locationClass.annotations

        parameters.forEach { parameter ->
            val parameterName = parameter.name
            if (parameterName != null && parameter.kind == KParameter.Kind.VALUE) {
                val serializer = serializer(parameter.type)

                element(
                    parameterName,
                    serializer.descriptor,
                    parameter.annotations,
                    parameter.isOptional
                )
            }
        }
    }
}

private fun LocationInfo.isKotlinObject(): Boolean =
    serialDescriptor.kind == StructureKind.OBJECT

private class LocationPropertyInfoImpl(
    name: String,
    val kGetter: KProperty1.Getter<Any, Any?>,
    isOptional: Boolean
) : LocationPropertyInfo(name, isOptional)
