package io.ktor.util

import io.ktor.features.*
import io.ktor.http.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Operator function that allows to delegate variables by call parameters.
 * It does conversion to type [R] using [DefaultConversionService]
 *
 * Example
 *
 * ```
 * get("/") {
 *     val page: Int by call.request.queryParameters
 *     val query: String by call.request.queryParameters
 *     // ...
 * }
 * ```
 *
 * @throws MissingRequestParameterException if no values associated with [name]
 * @throws ParameterConversionException when conversion from String to [R] fails
 */
@KtorExperimentalAPI
inline operator fun <reified R : Any> Parameters.getValue(thisRef: Any?, property: KProperty<*>): R {
    return getOrFail<R>(property.name)
}

/**
 * Get parameters value associated with this [name] or fail with [MissingRequestParameterException]
 * @throws MissingRequestParameterException if no values associated with this [name]
 */
@KtorExperimentalAPI
@Suppress("NOTHING_TO_INLINE")
inline fun Parameters.getOrFail(name: String): String {
    return get(name) ?: throw MissingRequestParameterException(name)
}

/**
 * Get parameters value associated with this [name] converting to type [R] using [DefaultConversionService]
 * or fail with [MissingRequestParameterException]
 * @throws MissingRequestParameterException if no values associated with this [name]
 * @throws ParameterConversionException when conversion from String to [R] fails
 */
@KtorExperimentalAPI
inline fun <reified R : Any> Parameters.getOrFail(name: String): R {
    val o = object {
        @Suppress("unused")
        val reflectField: R? = null
    }
    val type = o.javaClass.declaredFields.first { it.name == "reflectField" }.genericType
    return getOrFailImpl(name, R::class, type)
}

@PublishedApi
internal fun <R : Any> Parameters.getOrFailImpl(name: String, type: KClass<R>, javaType: Type): R {
    val values = getAll(name) ?: throw MissingRequestParameterException(name)
    return try {
        type.cast(DefaultConversionService.fromValues(values, javaType))
    } catch (cause: Exception) {
        throw ParameterConversionException(name, type.jvmName, cause)
    }
}

