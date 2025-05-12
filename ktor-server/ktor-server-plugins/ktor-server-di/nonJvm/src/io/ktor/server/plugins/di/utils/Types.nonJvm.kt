/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.InternalAPI

/**
 * This kind of reflection is currently only supported for the JVM platform.
 *
 * For non-JVM platforms, we omit support for resolving covariant types of declared dependencies.
 */
@InternalAPI
public actual fun TypeInfo.hierarchy(): Sequence<TypeInfo> =
    sequenceOf(this)

/**
 * This kind of reflection is currently only supported for the JVM platform.
 *
 * For non-JVM platforms, we omit support for resolving covariant types of declared dependencies.
 */
@InternalAPI
public actual fun TypeInfo.toNullable(): TypeInfo? = null

/**
 * This kind of reflection is currently only supported for the JVM platform.
 *
 * For non-JVM platforms, we omit support for resolving covariant types of declared dependencies.
 */
@InternalAPI
public actual fun TypeInfo.typeParametersHierarchy(): Sequence<TypeInfo> =
    sequenceOf(this)
