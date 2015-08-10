package org.jetbrains.ktor.locations

import org.jetbrains.ktor.routing.*
import java.lang
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

class InconsistentRoutingException(message: String) : Exception(message)

open public class LocationService {
    private val rootUri = UriInfo("", emptyList())
    private val info = hashMapOf<KClass<*>, LocationInfo>()

    private class LocationInfoProperty(val name: String, val getter: KProperty1.Getter<*, *>, val isOptional: Boolean)

    private data class UriInfo(val path: String, val query: List<Pair<String, String>>)
    private data class LocationInfo(val klass: KClass<*>,
                                    val parent: LocationInfo?,
                                    val parentParameter: LocationInfoProperty?,
                                    val path: String,
                                    val pathParameters: List<LocationInfoProperty>,
                                    val queryParameters: List<LocationInfoProperty>) {

        private val constructor = klass.constructors.single()

        fun create(request: RoutingApplicationRequest): Any {
            val parameters = constructor.parameters
            val args = Array(parameters.size()) { index ->
                val parameter = parameters[index]
                val parameterType = parameter.type
                val javaParameterType = parameterType.javaType
                val parameterName = parameter.name
                if (parent != null && parameterType.javaType === parent.klass.java) {
                    parent.create(request)
                } else {
                    val requestParameters = request.parameters[parameterName]
                    if (requestParameters == null) {
                        if (!parameterType.isMarkedNullable) {
                            throw InconsistentRoutingException("Parameter '$parameterName' required to construct '$klass' was not found in the request")
                        }
                        null
                    } else {
                        requestParameters.convertTo(javaParameterType)
                    }
                }
            }
            return constructor.call(*args)
        }

        fun String.convertTo(type: Type): Any {
            return when (type) {
                is WildcardType -> convertTo(type.upperBounds.single())
                javaClass<Int>(), javaClass<lang.Integer>() -> toInt()
                javaClass<Float>(), javaClass<lang.Float>() -> toFloat()
                javaClass<Double>(), javaClass<lang.Double>() -> toDouble()
                javaClass<Long>(), javaClass<lang.Long>() -> toLong()
                javaClass<Boolean>(), javaClass<lang.Boolean>() -> toBoolean()
                javaClass<String>(), javaClass<lang.String>() -> this
                else -> throw UnsupportedOperationException("Type $type is not supported in automatic location data class processing")
            }
        }

        fun List<String>.convertTo(type: Type): Any {
            if (type is ParameterizedType) {
                val rawType = type.rawType as Class<*>
                if (rawType.isAssignableFrom(List::class.java)) {
                    val itemType = type.actualTypeArguments.single()
                    return map { it.convertTo(itemType) }
                }
            }

            if (size() != 1) {
                throw InconsistentRoutingException("There are multiply values in request when trying to construct single value $type")
            }

            return get(0).convertTo(type)
        }
    }

    private fun UriInfo.combine(relativePath: String, queryValues: List<Pair<String, String>>): UriInfo {
        val combinedPath = (pathToParts(path) + pathToParts(relativePath)).join("/", "/")
        return UriInfo(combinedPath, query + queryValues)
    }

    inline fun <reified T:Annotation> KAnnotatedElement.annotation() : T? {
        return annotations.singleOrNull { it.annotationType() == T::class.java } as T?
    }

    private fun getOrCreateInfo(dataClass: KClass<*>): LocationInfo {
        return info.getOrPut(dataClass) {
            val parentClass = dataClass.java.enclosingClass?.kotlin
            val parentAnnotation = parentClass?.annotation<location>()
            val parent = parentAnnotation?.let {
                getOrCreateInfo(parentClass!!)
            }

            val path = dataClass.annotation<location>()?.let {
                it.path
            } ?: ""

            // TODO: use primary ctor parameters
            val declaredProperties = dataClass.memberProperties.map {
                LocationInfoProperty(it.name, (it as KProperty1<out Any?, *>).getter, it.returnType.isMarkedNullable)
            }

            val parentParameter = declaredProperties.firstOrNull {
                it.getter.returnType.javaType === parentClass?.java
            }

            if (parent != null && parentParameter == null) {
                if (parent.parentParameter != null)
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because it is chained to its parent")
                if (parent.pathParameters.any { !it.isOptional })
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because of non-optional path parameters ${parent.pathParameters.filter { !it.isOptional }}")
                if (parent.queryParameters.any { !it.isOptional })
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because of non-optional query parameters ${parent.queryParameters.filter { !it.isOptional }}")
            }

            val pathParameterNames = pathToParts(path).map {
                when {
                    it.startsWith("**") -> {
                        val tailcard = it.drop(2)
                        if (tailcard.isEmpty())
                            null
                        else
                            tailcard
                    }
                    it.startsWith(":?") -> it.drop(2)
                    it.startsWith(":") -> it.drop(1)
                    else -> null
                }
            }.filterNotNull().toSet()

            val declaredParameterNames = declaredProperties.map { it.name }.toSet()
            val invalidParameters = pathParameterNames.filter { it !in declaredParameterNames }
            if (invalidParameters.any()) {
                throw InconsistentRoutingException("Path parameters '$invalidParameters' are not bound to '$dataClass' properties")
            }

            val pathParameters = declaredProperties.filter { it.name in pathParameterNames }
            val queryParameters = declaredProperties.filterNot { pathParameterNames.contains(it.name) || it == parentParameter }
            LocationInfo(dataClass, parent, parentParameter, path, pathParameters, queryParameters)
        }
    }

    fun resolve<T : Any>(dataClass: KClass<*>, request: RoutingApplicationRequest): T {
        return getOrCreateInfo(dataClass).create(request) as T
    }


    private fun pathAndQuery(location: Any): UriInfo {
        val info = getOrCreateInfo(location.javaClass.kotlin)

        fun propertyValue(instance: Any, name: String): String? {
            // TODO: Cache properties by name in info
            val valueGetter = info.klass.memberProperties.single { it.name == name }
            val value = valueGetter.call(instance)
            if (value is Iterable<*>)
                return value.joinToString("/")
            return value?.toString()
        }

        val substituteParts = pathToParts(info.path).map {
            when {
                it.startsWith("**") -> propertyValue(location, it.drop(2))
                it.startsWith(":?") -> propertyValue(location, it.drop(2))
                it.startsWith(":") -> propertyValue(location, it.drop(1))
                else -> it
            }
        }

        val relativePath = substituteParts.filterNotNull().filterNot { it.isEmpty() }.join("/")

        val parentInfo = if (info.parent == null)
            rootUri
        else if (info.parentParameter != null) {
            val enclosingLocation = info.parentParameter.getter.call(location)!!
            pathAndQuery(enclosingLocation)
        } else {
            UriInfo(info.parent.path, emptyList())
        }

        val queryValues = info.queryParameters
                .flatMap { property ->
                    val value = property.getter.call(location)
                    when (value) {
                        null -> emptyList<Pair<String, String>>()
                        is Iterable<*> -> value.map { property.name to it.toString() }
                        else -> listOf(property.name to value.toString())
                    }
                }

        return parentInfo.combine(relativePath, queryValues)
    }

    fun href(location: Any): String {
        val info = pathAndQuery(location)
        return info.path + if (info.query.any())
            "?" + info.query.map { "${it.first}=${it.second}" }.joinToString("&")
        else
            ""
    }

    private fun createEntry(parent: RoutingEntry, info: LocationInfo): RoutingEntry {
        val hierarchyEntry = info.parent?.let { createEntry(parent, it) } ?: parent
        val pathEntry = createRoutingEntry(hierarchyEntry, info.path)

        val queryEntry = info.queryParameters.fold(pathEntry) { entry, query ->
            val selector = if (query.isOptional)
                OptionalParameterRoutingSelector(query.name)
            else
                ParameterRoutingSelector(query.name)
            entry.select(selector)
        }
        return queryEntry
    }

    fun createEntry(parent: RoutingEntry, dataClass: KClass<*>): RoutingEntry {
        return createEntry(parent, getOrCreateInfo(dataClass))
    }
}
