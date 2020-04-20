/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.reflect.*

/**
 * Data conversion service that does serialization and deserialization to/from list of strings
 */
expect interface ConversionService {
    /**
     * Deserialize [values] to an instance of [type]
     */
    fun fromValues(values: List<String>, type: KType): Any?

    /**
     * Serialize a [value] to values list
     */
    fun toValues(value: Any?): List<String>

    /**
     * Provides a list of types that this service supports.
     */
    fun supportedTypes(): List<KType>
}

/**
 * Deserialize [values] to an instance of type [T].
 */
@ExperimentalStdlibApi
inline fun <reified T : Any> ConversionService.fromValues(values: List<String>): T? {
    return fromValues(values, typeOf<T>()) as T?
}

/**
 * Thrown when failed to convert value
 */
class DataConversionException(message: String = "Invalid data format") : Exception(message)
