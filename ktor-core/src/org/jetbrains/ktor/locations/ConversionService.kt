package org.jetbrains.ktor.locations

import org.jetbrains.ktor.routing.*
import java.lang
import java.lang.reflect.*

public interface ConversionService {
    fun fromContext(context: RoutingApplicationRequestContext, name: String, type: Type, optional: Boolean): Any?
    fun toURI(value: Any?, name: String, optional: Boolean): List<String>
}

public open class DefaultConversionService : ConversionService {
    @suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun toURI(value: Any?, name: String, optional: Boolean): List<String> {
        return when (value) {
            null -> listOf<String>()
            is Iterable<*> -> value.flatMap { toURI(it, name, optional) }
            else -> {
                val type = value.javaClass
                listOf(when (type) {
                           javaClass<Int>(), javaClass<lang.Integer>(),
                           javaClass<Float>(), javaClass<lang.Float>(),
                           javaClass<Double>(), javaClass<lang.Double>(),
                           javaClass<Long>(), javaClass<lang.Long>(),
                           javaClass<Boolean>(), javaClass<lang.Boolean>(),
                           javaClass<String>(), javaClass<lang.String>() -> value.toString()
                           else -> throw UnsupportedOperationException("Type $type is not supported in automatic location data class processing")
                       })
            }
        }
    }

    @suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    open fun convert(value: String, type: Type): Any {
        return when (type) {
            is WildcardType -> convert(value, type.upperBounds.single())
            javaClass<Int>(), javaClass<lang.Integer>() -> value.toInt()
            javaClass<Float>(), javaClass<lang.Float>() -> value.toFloat()
            javaClass<Double>(), javaClass<lang.Double>() -> value.toDouble()
            javaClass<Long>(), javaClass<lang.Long>() -> value.toLong()
            javaClass<Boolean>(), javaClass<lang.Boolean>() -> value.toBoolean()
            javaClass<String>(), javaClass<lang.String>() -> value
            else -> throw UnsupportedOperationException("Type $type is not supported in automatic location data class processing")
        }
    }

    open fun convert(values: List<String>, type: Type): Any {
        if (type is ParameterizedType) {
            val rawType = type.rawType as Class<*>
            if (rawType.isAssignableFrom(List::class.java)) {
                val itemType = type.actualTypeArguments.single()
                return values.map { convert(it, itemType) }
            }
        }

        if (values.size() != 1) {
            throw InconsistentRoutingException("There are multiply values in request when trying to construct single value $type")
        }

        return convert(values.single(), type)
    }

    override fun fromContext(context: RoutingApplicationRequestContext, name: String, type: Type, optional: Boolean): Any? {
        val requestParameters = context.parameters[name]
        return if (requestParameters == null) {
            if (!optional) {
                throw InconsistentRoutingException("Parameter '$name' was not found in the request")
            }
            null
        } else {
            convert(requestParameters, type)
        }
    }
}