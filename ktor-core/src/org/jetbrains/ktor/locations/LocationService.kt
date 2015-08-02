package org.jetbrains.ktor.locations

import org.jetbrains.ktor.routing.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

open public class LocationService {
    fun resolve<T : Any> (dataClass: KClass<*>, request: LocationRoutingApplicationRequest<T>): T {
        val enclosingClass = dataClass.java.enclosingClass?.kotlin

        val constructor = dataClass.constructors.single()
        val parameters = constructor.parameters
        val javaParameters = constructor.javaConstructor!!.parameters
        val args = Array(parameters.size()) { index ->
            val parameter = parameters[index]
            val parameterType = parameter.type
            val javaParameterType = javaParameters[index].type!!
            val parameterName = parameter.name
            if (enclosingClass != null && javaParameterType === enclosingClass.java) {
                enclosingClass.java.getAnnotation(javaClass<at>())?.let {
                    resolve(enclosingClass, request)
                }
            } else {
                val requestParameters = request.parameters[parameterName]
                requestParameters?.singleOrNull()?.convertTo(javaParameterType)
            }
        }
        return constructor.call(*args) as T
    }

    data class hrefInfo(val path: String, val query: List<Pair<String, String>>) {
        fun combine(relativePath: String, queryValues: List<Pair<String, String>>): hrefInfo {
            val combinedPath = (pathToParts(path) + pathToParts(relativePath)).join("/", "/")
            return hrefInfo(combinedPath, query + queryValues)
        }

        companion object {
            val root = hrefInfo("", emptyList())
        }
    }

    fun pathAndQuery(location: Any): hrefInfo {
        val dataClass = location.javaClass.kotlin
        val at = dataClass.java.getAnnotation(javaClass<at>())
        val relativeParts = if (at == null) pathToParts("/") else pathToParts(at.url)
        val usedProperties = arrayListOf<String>()

        fun locationValue(name: String): String? {
            usedProperties.add(name)
            val valueGetter = dataClass.memberProperties.single { it.name == name }
            return valueGetter.get(location)?.toString()
        }

        val substituteParts = relativeParts.map {
            when {
                it.startsWith("**") -> locationValue(it.drop(2)) ?: ""
                it.startsWith(":?") -> locationValue(it.drop(2)) ?: "null"
                it.startsWith(":") -> locationValue(it.drop(1)) ?: "null"
                else -> it
            }
        }

        val relativePath = substituteParts.filterNot { it.isEmpty() }.join("/")

        val enclosingClass = dataClass.java.enclosingClass?.kotlin
        val parentInfo = if (enclosingClass != null) {
            val enclosingAt = enclosingClass.java.getAnnotation(javaClass<at>())
            if (enclosingAt != null) {
                val enclosingProperty = dataClass.memberProperties.singleOrNull {
                    it.getter.javaMethod!!.returnType == enclosingClass.java
                }
                if (enclosingProperty != null) {
                    usedProperties.add(enclosingProperty.name)
                    val enclosingLocation = enclosingProperty.call(location)!!
                    pathAndQuery(enclosingLocation)
                } else {
                    hrefInfo(at.url, emptyList())
                }
            } else hrefInfo.root
        } else hrefInfo.root


        val queryValues = dataClass.memberProperties
                .filterNot { usedProperties.contains(it.name) }
                .map { property -> locationValue(property.name)?.let { value -> property.name to value } }
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

    fun <T> createEntry(parent: RoutingEntry, dataClass: Class<T>): RoutingEntry {
        val enclosingClass = dataClass.enclosingClass
        val hierarchyEntry = if (enclosingClass != null) {
            val annotation = enclosingClass.getAnnotation(javaClass<at>())
            if (annotation != null)
                createEntry(parent, enclosingClass)
            else
                parent
        } else
            parent

        val annotation = dataClass.getAnnotation(javaClass<at>())
        if (annotation == null)
            return createRoutingEntry(hierarchyEntry, "/") { RoutingEntry() }
        return createRoutingEntry(hierarchyEntry, annotation.url) { RoutingEntry() }
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
