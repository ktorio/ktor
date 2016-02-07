package org.jetbrains.ktor.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

class InconsistentRoutingException(message: String) : Exception(message)

open public class Locations(val conversionService: ConversionService) {
    private val rootUri = ResolvedUriInfo("", emptyList())
    private val info = hashMapOf<KClass<*>, LocationInfo>()

    private class LocationInfoProperty(val name: String, val getter: KProperty1.Getter<*, *>, val isOptional: Boolean)

    private data class ResolvedUriInfo(val path: String, val query: List<Pair<String, String>>)
    private data class LocationInfo(val klass: KClass<*>,
                                    val parent: LocationInfo?,
                                    val parentParameter: LocationInfoProperty?,
                                    val path: String,
                                    val pathParameters: List<LocationInfoProperty>,
                                    val queryParameters: List<LocationInfoProperty>)

    private fun LocationInfo.create(request: RoutingApplicationCall): Any {
        val constructor: KFunction<Any> = klass.primaryConstructor ?: klass.constructors.single()
        val parameters = constructor.parameters
        val arguments = parameters.map { parameter ->
            val parameterType = parameter.type
            val parameterName = parameter.name ?: getParameterNameFromAnnotation(parameter)
            val value: Any? = if (parent != null && parameterType == parent.klass.defaultType) {
                parent.create(request)
            } else {
                conversionService.fromContext(request, parameterName, parameterType.javaType, parameter.isOptional)
            }
            parameter to value
        }.filterNot { it.first.isOptional && it.second == null }.toMap()

        return constructor.callBy(arguments)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun getParameterNameFromAnnotation(parameter: KParameter): String = TODO()

    private fun ResolvedUriInfo.combine(relativePath: String, queryValues: List<Pair<String, String>>): ResolvedUriInfo {
        val pathElements = (path.split("/") + relativePath.split("/")).filterNot { it.isEmpty() }
        val combinedPath = pathElements.joinToString("/", "/")
        return ResolvedUriInfo(combinedPath, query + queryValues)
    }

    inline fun <reified T : Annotation> KAnnotatedElement.annotation(): T? {
        return annotations.singleOrNull { it.annotationClass == T::class } as T?
    }

    private fun getOrCreateInfo(dataClass: KClass<*>): LocationInfo {
        return info.getOrPut(dataClass) {
            val parentClass = dataClass.java.enclosingClass?.kotlin
            val parent = parentClass?.annotation<location>()?.let {
                getOrCreateInfo(parentClass)
            }

            val path = dataClass.annotation<location>()?.let { it.path } ?: ""

            val constructor: KFunction<Any> = dataClass.primaryConstructor ?: dataClass.constructors.single()

            val declaredProperties = constructor.parameters.map { parameter ->
                val property = dataClass.declaredMemberProperties.singleOrNull { property -> property.name == parameter.name }
                if (property == null) {
                    throw InconsistentRoutingException("Parameter ${parameter.name} of constructor for class ${dataClass.qualifiedName} should have corresponding property")
                }
                LocationInfoProperty(parameter.name ?: "<unnamed>", (property as KProperty1<out Any?, *>).getter, parameter.isOptional)
            }

            val parentParameter = declaredProperties.firstOrNull {
                it.getter.returnType == parentClass?.defaultType
            }

            if (parent != null && parentParameter == null) {
                if (parent.parentParameter != null)
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because it is chained to its parent")
                if (parent.pathParameters.any { !it.isOptional })
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because of non-optional path parameters ${parent.pathParameters.filter { !it.isOptional }}")
                if (parent.queryParameters.any { !it.isOptional })
                    throw InconsistentRoutingException("Nested location '$dataClass' should have parameter for parent location because of non-optional query parameters ${parent.queryParameters.filter { !it.isOptional }}")
            }

            val pathParameterNames = RoutingPath.parse(path).parts.filter {
                it.kind == RoutingPathSegmentKind.Parameter || it.kind == RoutingPathSegmentKind.TailCard
            }.map { it.value }
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

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(dataClass: KClass<*>, request: RoutingApplicationCall): T {
        return getOrCreateInfo(dataClass).create(request) as T
    }

    // TODO: optimize allocations
    private fun pathAndQuery(location: Any): ResolvedUriInfo {
        val info = getOrCreateInfo(location.javaClass.kotlin)

        fun propertyValue(instance: Any, name: String): List<String> {
            // TODO: Cache properties by name in info
            val property = info.pathParameters.single { it.name == name }
            val value = property.getter.call(instance)
            return conversionService.toURI(value, name, property.getter.returnType.isMarkedNullable)
        }

        val substituteParts = RoutingPath.parse(info.path).parts.flatMap { it ->
            when (it.kind) {
                RoutingPathSegmentKind.Constant -> listOf(it.value)
                else -> propertyValue(location, it.value)
            }
        }

        val relativePath = substituteParts.filterNotNull().filterNot { it.isEmpty() }.map { encodeURLPart(it) }.joinToString("/")

        val parentInfo = if (info.parent == null)
            rootUri
        else if (info.parentParameter != null) {
            val enclosingLocation = info.parentParameter.getter.call(location)!!
            pathAndQuery(enclosingLocation)
        } else {
            ResolvedUriInfo(info.parent.path, emptyList())
        }

        val queryValues = info.queryParameters
                .flatMap { property ->
                    val value = property.getter.call(location)
                    conversionService.toURI(value, property.name, property.isOptional).map { property.name to it }
                }

        return parentInfo.combine(relativePath, queryValues)
    }

    fun href(location: Any): String {
        val info = pathAndQuery(location)
        return info.path + if (info.query.any())
            "?" + info.query.map { "${it.first}=${it.second.encodeURL()}" }.joinToString("&")
        else
            ""
    }

    internal fun href(location: Any, builder: URLBuilder) {
        val info = pathAndQuery(location)
        builder.encodedPath = info.path
        for ((name, value) in info.query) {
            builder.parameters.append(name, value)
        }
    }

    private fun createEntry(parent: RoutingEntry, info: LocationInfo): RoutingEntry {
        val hierarchyEntry = info.parent?.let { createEntry(parent, it) } ?: parent
        val pathEntry = hierarchyEntry.createRoute(info.path)

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

    companion object LocationsFeature : ApplicationFeature<Locations> {
        override fun install(application: Application, configure: Locations.() -> Unit): Locations {
            return Locations(DefaultConversionService()).apply(configure)
        }

        override val key: AttributeKey<Locations> = AttributeKey("Locations")
        override val name: String = "Locations"
    }
}

