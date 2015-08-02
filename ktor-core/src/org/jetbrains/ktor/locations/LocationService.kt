package org.jetbrains.ktor.locations

import org.jetbrains.ktor.routing.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

open public class LocationService {
    val rootUri = UriInfo("", emptyList())

    class LocationInfoProperty(val name: String, val getter: KProperty1.Getter<*, *>, val isOptional: Boolean)

    data class UriInfo(val path: String, val query: List<Pair<String, String>>)
    data class LocationInfo(val klass: KClass<*>,
                            val parent: LocationInfo?,
                            val parentParameter: LocationInfoProperty?,
                            val path: String,
                            val pathParameters: List<LocationInfoProperty>,
                            val queryParameters: List<LocationInfoProperty>) {

        private val constructor = klass.constructors.single()

        fun create(request: RoutingApplicationRequest): Any {
            val parameters = constructor.parameters
            val javaParameters = constructor.javaConstructor!!.parameters
            val args = Array(parameters.size()) { index ->
                val parameter = parameters[index]
                val parameterType = parameter.type
                val javaParameterType = javaParameters[index].type!!
                val parameterName = parameter.name
                if (parent != null && javaParameterType === parent.klass.java) {
                    parent.create(request)
                } else {
                    val requestParameters = request.parameters[parameterName]
                    if (requestParameters == null) {
                        if (!parameterType.isMarkedNullable) {
                            throw IllegalArgumentException("Parameter '$parameterName' required to construct '$klass' was not found in request")
                        }
                        null
                    } else {
                        if (requestParameters.size() != 1) {
                            throw IllegalArgumentException("There are multiply '$parameterName' parameters when trying to construct $klass")
                        }
                        requestParameters[0].convertTo(javaParameterType)
                    }
                }
            }
            return constructor.call(*args)!!
        }
    }

    fun UriInfo.combine(relativePath: String, queryValues: List<Pair<String, String>>): UriInfo {
        val combinedPath = (pathToParts(path) + pathToParts(relativePath)).join("/", "/")
        return UriInfo(combinedPath, query + queryValues)
    }

    val info = hashMapOf<KClass<*>, LocationInfo>()

    fun getOrCreateInfo(dataClass: KClass<*>): LocationInfo {
        return info.getOrPut(dataClass) {
            val enclosingClass = dataClass.java.enclosingClass?.kotlin
            val parent = enclosingClass?.java?.getAnnotation(javaClass<location>())?.let {
                getOrCreateInfo(enclosingClass!!)
            }

            val path = dataClass.java.getAnnotation(javaClass<location>())?.let {
                it.path
            } ?: ""

            // TODO: use primary ctor parameters
            val declaredProperties = dataClass.memberProperties.map {
                LocationInfoProperty(it.name, (it as KProperty1<Any?, *>).getter, it.returnType.isMarkedNullable)
            }

            val parentParameter = declaredProperties.firstOrNull {
                it.getter.javaMethod?.returnType === enclosingClass?.java
            }

            val pathParameterNames = pathToParts(path).map {
                when {
                    it.startsWith("**") -> it.drop(2)
                    it.startsWith(":?") -> it.drop(2)
                    it.startsWith(":") -> it.drop(1)
                    else -> null
                }
            }.filterNotNull().toSet()

            val declaredParameterNames = declaredProperties.map { it.name }.toSet()
            val invalidParameters = pathParameterNames.filter { it !in declaredParameterNames }
            if (invalidParameters.any()) {
                throw IllegalArgumentException("Parameters '$invalidParameters' are not bound to '$dataClass' properties")
            }

            val pathParameters = declaredProperties.filter { it.name in pathParameterNames }
            val queryParameters = declaredProperties.filterNot { pathParameterNames.contains(it.name) || it == parentParameter }
            LocationInfo(dataClass, parent, parentParameter, path, pathParameters, queryParameters)
        }
    }


    fun resolve<T : Any>(dataClass: KClass<*>, request: LocationRoutingApplicationRequest<T>): T {
        return getOrCreateInfo(dataClass).create(request) as T
    }


    fun pathAndQuery(location: Any): UriInfo {
        val info = getOrCreateInfo(location.javaClass.kotlin)

        fun propertyValue(instance: Any, name: String): Any? {
            // TODO: Cache properties by name in info
            val valueGetter = info.klass.memberProperties.single { it.name == name }
            return valueGetter.call(instance)
        }

        val substituteParts = pathToParts(info.path).map {
            when {
                it.startsWith("**") -> propertyValue(location, it.drop(2)).toString()
                it.startsWith(":?") -> propertyValue(location, it.drop(2)).toString()
                it.startsWith(":") -> propertyValue(location, it.drop(1)).toString()
                else -> it
            }
        }

        val relativePath = substituteParts.filterNot { it.isEmpty() }.join("/")

        val parentInfo = if (info.parent != null && info.parentParameter != null) {
            val enclosingLocation = info.parentParameter.getter.call(location)!!
            pathAndQuery(enclosingLocation)
        } else rootUri

        val queryValues = info.queryParameters
                .map { property -> property.getter.call(location)?.let { value -> property.name to value.toString() } }
                .filterNotNull()

        return parentInfo.combine(relativePath, queryValues)
    }

    fun href(location: Any): String {
        val info = pathAndQuery(location)
        return info.path + if (info.query.any())
            "?" + info.query.map { "${it.first}=${it.second}" }.joinToString("&")
        else
            ""
    }

    fun createEntry(parent: RoutingEntry, info: LocationInfo): RoutingEntry {
        val hierarchyEntry = info.parent?.let { createEntry(parent, it) } ?: parent
        val pathEntry = createRoutingEntry(hierarchyEntry, info.path) { RoutingEntry() }

        return info.queryParameters.fold(pathEntry) { entry, query ->
            val selector = if (query.isOptional)
                OptionalParameterRoutingSelector(query.name)
            else
                ParameterRoutingSelector(query.name)
            entry.add(selector, RoutingEntry())
        }
    }

    fun createEntry(parent: RoutingEntry, dataClass: KClass<*>): RoutingEntry {
        return createEntry(parent, getOrCreateInfo(dataClass))
    }

    fun routing(routing: Routing): LocationRouting = LocationRouting(this, routing)
}

fun String.convertTo(type: Class<*>): Any {
    return when (type) {
        javaClass<Int>() -> toInt()
        javaClass<Float>() -> toFloat()
        javaClass<Double>() -> toDouble()
        javaClass<Long>() -> toLong()
        javaClass<Boolean>() -> toBoolean()
        javaClass<String>() -> this
        else -> throw UnsupportedOperationException("Type $type is not supported in automatic location data class processing")
    }
}
