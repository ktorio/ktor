package org.jetbrains.ktor.locations

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import java.lang.reflect.*

public interface ConversionService {
    fun fromContext(call: RoutingApplicationCall, name: String, type: Type, optional: Boolean): Any?
    fun toURI(value: Any?, name: String, optional: Boolean): List<String>
}

public open class DefaultConversionService : ConversionService {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun toURI(value: Any?, name: String, optional: Boolean): List<String> {
        return when (value) {
            null -> listOf<String>()
            is Iterable<*> -> value.flatMap { toURI(it, name, optional) }
            else -> {
                val type = value.javaClass
                listOf(when (type) {
                    Int::class.java, java.lang.Integer::class.java,
                    Float::class.java, java.lang.Float::class.java,
                    Double::class.java, java.lang.Double::class.java,
                    Long::class.java, java.lang.Long::class.java,
                    Boolean::class.java, java.lang.Boolean::class.java,
                    String::class.java, java.lang.String::class.java -> value.toString()
                    else -> {
                        if (type.isEnum) {
                            (value as Enum<*>).name
                        } else
                            throw UnsupportedOperationException("Type $type is not supported in automatic location data class processing")
                    }
                })
            }
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    open fun convert(value: String, type: Type): Any {
        return when (type) {
            is WildcardType -> convert(value, type.upperBounds.single())
            Int::class.java, java.lang.Integer::class.java -> value.toInt()
            Float::class.java, java.lang.Float::class.java -> value.toFloat()
            Double::class.java, java.lang.Double::class.java -> value.toDouble()
            Long::class.java, java.lang.Long::class.java -> value.toLong()
            Boolean::class.java, java.lang.Boolean::class.java -> value.toBoolean()
            String::class.java, java.lang.String::class.java -> value
            else ->
                if (type is Class<*> && type.isEnum) {
                    type.enumConstants.first { (it as Enum<*>).name == value }
                } else
                    throw UnsupportedOperationException("Type $type is not supported in automatic location data class processing")

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

        if (values.size != 1) {
            throw InconsistentRoutingException("There are multiply values in request when trying to construct single value $type")
        }

        return convert(values.single(), type)
    }

    override fun fromContext(call: RoutingApplicationCall, name: String, type: Type, optional: Boolean): Any? {
        val requestParameters = call.parameters.getAll(name)
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