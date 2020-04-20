/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.features.*
import io.ktor.http.*
import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.*

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
 * @throws MissingRequestParameterException if no values associated with name
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
@OptIn(ExperimentalStdlibApi::class)
inline fun <reified R : Any> Parameters.getOrFail(name: String): R {
    return getOrFailImpl(name, R::class, typeOf<R>())
}

@PublishedApi
internal fun <R : Any> Parameters.getOrFailImpl(name: String, clazz: KClass<R>, type: KType): R {
    val values = getAll(name) ?: throw MissingRequestParameterException(name)
    return try {
        clazz.cast(DefaultConversionService.fromValues(values, type))
    } catch (cause: Exception) {
        throw ParameterConversionException(name, type.toString(), cause)
    }
}

@PublishedApi
@Suppress("unused") // for binary compatibility
internal fun <R : Any> Parameters.getOrFailImpl(name: String, clazz: KClass<R>, type: Type): R {
    val values = getAll(name) ?: throw MissingRequestParameterException(name)
    return try {
        // the default conversion service does support java.lang.Type for backward compatibility for now
        // so we may suppress the error here
        @Suppress("DEPRECATION_ERROR")
        clazz.cast(DefaultConversionService.fromValues(values, type))
    } catch (cause: Exception) {
        throw ParameterConversionException(name, type.toString(), cause)
    }
}
