/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import java.lang.reflect.*
import kotlin.reflect.*

/**
 * Data conversion service that does serialization and deserialization to/from list of strings
 */
actual interface ConversionService {
    /**
     * Deserialize [values] to an instance of [type]
     */
    actual fun fromValues(values: List<String>, type: KType): Any?

    /**
     * Deserialize [values] to an instance of [type]
     * This is no longer invoked by ktor and should not be used anymore.
     * It will be removed in future releases.
     */
    @Deprecated(
        "Using java.lang.Type is deprecated. Use fromValues(_, KType) instead.",
        level = DeprecationLevel.ERROR
    )
    fun fromValues(values: List<String>, type: Type): Any? {
        TODO("This is no longer used.")
    }

    /**
     * Serialize a [value] to values list
     */
    actual fun toValues(value: Any?): List<String>

    /**
     * Provides a list of types that this service supports.
     */
    actual fun supportedTypes(): List<KType>
}
