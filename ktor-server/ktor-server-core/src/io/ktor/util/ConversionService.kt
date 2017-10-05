package io.ktor.util

import java.lang.reflect.*

interface ConversionService {
    fun fromValues(values: List<String>, type: Type): Any?
    fun toValues(value: Any?): List<String>
}

object DefaultConversionService : ConversionService {
    override fun toValues(value: Any?): List<String> = when (value) {
        null -> listOf()
        is Iterable<*> -> value.flatMap { toValues(it) }
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
                        throw DataConversionException("Type $type is not supported in default data conversion service")
                }
            })
        }
    }

    override fun fromValues(values: List<String>, type: Type): Any {
        if (type is ParameterizedType) {
            val rawType = type.rawType as Class<*>
            if (rawType.isAssignableFrom(List::class.java)) {
                val itemType = type.actualTypeArguments.single()
                return values.map { convert(it, itemType) }
            }
        }

        when {
            values.isEmpty() -> throw DataConversionException("There are no values when trying to construct single value $type")
            values.size > 1 -> throw DataConversionException("There are multiply values when trying to construct single value $type")
            else -> return convert(values.single(), type)
        }
    }

    private fun convert(value: String, type: Type): Any = when (type) {
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
                throw DataConversionException("Type $type is not supported in default data conversion service")
    }

}

class DataConversionException(message: String = "Invalid data format") : Exception(message)