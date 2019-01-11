package io.ktor.util

import io.ktor.features.*
import io.ktor.http.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

@KtorExperimentalAPI
inline operator fun <reified R : Any> Parameters.getValue(thisRef: Any?, property: KProperty<*>): R {
    return getOrFail<R>(property.name)
}

@KtorExperimentalAPI
@Suppress("NOTHING_TO_INLINE")
inline fun Parameters.getOrFail(name: String): String {
    return get(name) ?: throw MissingRequestParameterException(name)
}

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

