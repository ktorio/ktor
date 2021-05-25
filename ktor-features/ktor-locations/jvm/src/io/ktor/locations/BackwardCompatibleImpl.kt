/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.locations

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.util.*
import java.lang.reflect.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

@OptIn(KtorExperimentalLocationsAPI::class)
internal abstract class LocationsImpl(
    protected val application: Application,
    protected val routeService: LocationRouteService
) {
    protected val info: MutableMap<KClass<*>, LocationInfo> = HashMap()

    protected val conversionService: ConversionService
        get() = application.conversionService

    val registeredLocations: List<LocationInfo>
        get() = Collections.unmodifiableList(info.values.toList())

    public abstract fun getOrCreateInfo(locationClass: KClass<*>): LocationInfo

    public abstract fun instantiate(info: LocationInfo, allParameters: Parameters): Any

    public abstract fun href(instance: Any): String

    public abstract fun href(location: Any, builder: URLBuilder)
}

@OptIn(KtorExperimentalLocationsAPI::class)
internal class BackwardCompatibleImpl(
    application: Application,
    routeService: LocationRouteService
) : LocationsImpl(application, routeService) {
    private data class ResolvedUriInfo(val path: String, val query: List<Pair<String, String>>)

    private val rootUri = ResolvedUriInfo("", emptyList())

    override fun getOrCreateInfo(locationClass: KClass<*>): LocationInfo {
        return info[locationClass] ?: getOrCreateInfo(locationClass, HashSet())
    }

    override fun instantiate(info: LocationInfo, allParameters: Parameters): Any {
        return info.create(allParameters)
    }

    override fun href(instance: Any): String {
        val info = pathAndQuery(instance)
        return info.path + if (info.query.any()) "?" + info.query.formUrlEncode() else ""
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
            return conversionService.toValues(value)
        }

        val substituteParts = RoutingPath.parse(info.path).parts.flatMap {
            when (it.kind) {
                RoutingPathSegmentKind.Constant -> listOf(it.value)
                RoutingPathSegmentKind.Parameter -> {
                    if (info.klass.objectInstance != null) {
                        throw IllegalArgumentException(
                            "There is no place to bind ${it.value} in object for '${info.klass}'"
                        )
                    }
                    propertyValue(location, PathSegmentSelectorBuilder.parseName(it.value))
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
            conversionService.toValues(value).map { property.name to it }
        }

        return parentInfo.combine(relativePath, queryValues)
    }

    private fun LocationInfo.create(allParameters: Parameters): Any {
        val objectInstance = klass.objectInstance
        if (objectInstance != null) return objectInstance

        val constructor: KFunction<Any> = klass.primaryConstructor ?: klass.constructors.single()
        val parameters = constructor.parameters
        val arguments = parameters.map { parameter ->
            val parameterType = parameter.type
            val parameterName = parameter.name ?: getParameterNameFromAnnotation(parameter)
            val value: Any? = if (parent != null && parameterType == parent.klass.starProjectedType) {
                parent.create(allParameters)
            } else {
                createFromParameters(allParameters, parameterName, parameterType.javaType, parameter.isOptional)
            }
            parameter to value
        }.filterNot { it.first.isOptional && it.second == null }.toMap()

        try {
            return constructor.callBy(arguments)
        } catch (cause: InvocationTargetException) {
            throw cause.cause ?: cause
        }
    }

    private fun createFromParameters(parameters: Parameters, name: String, type: Type, optional: Boolean): Any? {
        return when (val values = parameters.getAll(name)) {
            null -> when {
                !optional -> {
                    throw MissingRequestParameterException(name)
                }
                else -> null
            }
            else -> {
                try {
                    conversionService.fromValues(values, type)
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
                if (routeService.findRoute(outerClass) != null) getOrCreateInfo(outerClass, visited) else null
            }

            if (parentInfo != null && locationClass.isKotlinObject && parentInfo.klass.isKotlinObject) {
                application.log.warn(
                    "Object nesting in Ktor Locations is going to be deprecated. " +
                        "Convert nested object to a class with parameter. " +
                        "See https://github.com/ktorio/ktor/issues/1660 for more details."
                )
            }

            val path = routeService.findRoute(locationClass) ?: ""
            if (locationClass.objectInstance != null) {
                return@getOrPut LocationInfo(locationClass, parentInfo, null, path, emptyList(), emptyList())
            }

            val constructor: KFunction<Any> =
                locationClass.primaryConstructor
                    ?: locationClass.constructors.singleOrNull()
                    ?: throw IllegalArgumentException(
                        "Class $locationClass cannot be instantiated because the constructor is missing"
                    )

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
                if (parentInfo.parentParameter != null) {
                    throw LocationRoutingException(
                        "Nested location '$locationClass' should have parameter for parent location " +
                            "because it is chained to its parent"
                    )
                }
                if (parentInfo.pathParameters.any { !it.isOptional }) {
                    throw LocationRoutingException(
                        "Nested location '$locationClass' should have parameter for parent location " +
                            "because of non-optional path parameters " +
                            "${parentInfo.pathParameters.filter { !it.isOptional }}"
                    )
                }
                if (parentInfo.queryParameters.any { !it.isOptional }) {
                    throw LocationRoutingException(
                        "Nested location '$locationClass' should have parameter for parent location " +
                            "because of non-optional query parameters " +
                            "${parentInfo.queryParameters.filter { !it.isOptional }}"
                    )
                }

                if (!parentInfo.klass.isKotlinObject) {
                    application.log.warn(
                        "A nested location class should have a parameter with the type " +
                            "of the outer location class. " +
                            "See https://github.com/ktorio/ktor/issues/1660 for more details."
                    )
                }
            }

            val pathParameterNames = RoutingPath.parse(path).parts
                .filter { it.kind == RoutingPathSegmentKind.Parameter }
                .map { PathSegmentSelectorBuilder.parseName(it.value) }

            val declaredParameterNames = declaredProperties.map { it.name }.toSet()
            val invalidParameters = pathParameterNames.filter { it !in declaredParameterNames }
            if (invalidParameters.any()) {
                throw LocationRoutingException(
                    "Path parameters '$invalidParameters' are not bound to '$locationClass' properties"
                )
            }

            val pathParameters = declaredProperties.filter { it.name in pathParameterNames }
            val queryParameters =
                declaredProperties.filterNot { pathParameterNames.contains(it.name) || it == parentParameter }
            LocationInfo(locationClass, parentInfo, parentParameter, path, pathParameters, queryParameters)
        }
    }

    @KtorExperimentalLocationsAPI
    private val LocationPropertyInfo.getter: (Any) -> Any?
        get() = (this as LocationPropertyInfoImpl).kGetter

    private val KClass<*>.isKotlinObject: Boolean
        get() = isFinal && objectInstance != null
}
