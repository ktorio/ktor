/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * This kind of reflection is currently only supported for the JVM platform.
 *
 * For non-JVM platforms, we omit support for resolving covariant types of declared dependencies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.hierarchy)
 */
@InternalAPI
public actual fun TypeInfo.hierarchy(): Sequence<TypeInfo> =
    sequenceOf(this)

/**
 * Converts raw types without reflection. Converting [kotlinType] is only supported on the JVM platform.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.toNullable)
 */
@InternalAPI
public actual fun TypeInfo.toNullable(): TypeInfo? {
    if (isNullable || kotlinType != null) return null
    return TypeInfo(type = type, isNullable = true)
}

/**
 * This kind of reflection is currently only supported for the JVM platform.
 *
 * For non-JVM platforms, we omit support for resolving covariant types of declared dependencies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.typeParametersHierarchy)
 */
@InternalAPI
public actual fun TypeInfo.typeParametersHierarchy(): Sequence<TypeInfo> =
    sequenceOf(this)
