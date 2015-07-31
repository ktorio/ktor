package org.jetbrains.ktor.locations

import jet.runtime.typeinfo.*
import org.jetbrains.ktor.routing.*

open public class LocationService {
    fun resolve<T : Any> (dataClass: Class<*>, request: LocationRoutingApplicationRequest<T>): T {
        val enclosingClass = dataClass.enclosingClass

        val constructors = dataClass.constructors.filterNot {
            it.parameterTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
        }
        val constructor = constructors.single()
        val parameters = constructor.parameters
        val args = Array(constructor.parameterCount) { index ->
            val parameter = parameters[index]
            val parameterAnnotation = parameter.getAnnotation(javaClass<JetValueParameter>())
            val parameterType = parameter.type
            val parameterName = parameterAnnotation.name
            if (parameterType === enclosingClass)
                enclosingClass?.getAnnotation(javaClass<at>())?.let {
                    resolve(enclosingClass, request)
                }
            else
                request.parameters[parameterName]?.single()?.convertTo(parameterType)
        }
        return constructor.newInstance(*args) as T
    }

    fun href(location: Any): String {
        val dataClass = location.javaClass
        val at = dataClass.getAnnotation(javaClass<at>())
        val relativeParts = pathToParts(at.url)
        val usedProperties = arrayListOf<String>()

        fun locationValue(name: String): String? {
            usedProperties.add(name)
            val valueGetter = dataClass.methods.single { it.name.equals("get$name", ignoreCase = true) && it.parameterCount == 0 }
            return valueGetter(location)?.toString()
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

        val enclosingClass = dataClass.enclosingClass
        val parentUrl = if (enclosingClass != null) {
            val enclosingAt = enclosingClass.getAnnotation(javaClass<at>())
            if (enclosingAt != null) {
                val enclosingGetter = dataClass.methods.singleOrNull {
                    it.returnType == enclosingClass
                            && it.parameterCount == 0
                            && it.name.startsWith("get")
                }
                if (enclosingGetter != null) {
                    usedProperties.add(enclosingGetter.name.drop(3).decapitalize())
                    val enclosingLocation = enclosingGetter(location)
                    href(enclosingLocation)
                } else {
                    enclosingAt.url
                }
            } else null
        } else null


        val constructors = dataClass.constructors.filterNot {
            it.parameterTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
        }
        val parameters = constructors.single().parameters
        val queryValues = parameters
                .map {
                    val parameterAnnotation = it.getAnnotation(javaClass<JetValueParameter>())
                    parameterAnnotation.name
                }
                .filterNot { usedProperties.contains(it) }
                .map { property -> locationValue(property)?.let { value -> "${property}=${value.toString()}" } }
                .filterNotNull()
        val relativeUrl = relativePath + if (queryValues.any())
            "?" + queryValues.joinToString("&")
        else
            ""

        if (parentUrl != null)
            return "$parentUrl/$relativeUrl"

        return "/$relativeUrl"
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
