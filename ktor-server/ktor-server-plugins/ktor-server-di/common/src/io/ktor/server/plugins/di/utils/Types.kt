/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Returns all types of the hierarchy, starting with the given type.
 *
 * When type arguments are encountered, they are included in parent type arguments, so
 * for example, `Collection<Element>` is included for the root type `ArrayList<Element>`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.hierarchy)
 */
@InternalAPI
public expect fun TypeInfo.hierarchy(): Sequence<TypeInfo>

/**
 * Converts the current [TypeInfo] into a nullable type representation.
 * If the type is already nullable, returns null.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.toNullable)
 *
 * @return A new [TypeInfo] instance with a nullable type, or null if the type is already nullable.
 */
@InternalAPI
public expect fun TypeInfo.toNullable(): TypeInfo?

/**
 * Returns a list of the given base implementation for covariant type arguments.
 *
 * For example, supertype bounds with the `out` keyword will return matching supertypes for the type arguments.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.utils.typeParametersHierarchy)
 *
 * @return A list of [TypeInfo] representing the base type with covariant type arguments.
 */
@InternalAPI
public expect fun TypeInfo.typeParametersHierarchy(): Sequence<TypeInfo>
