/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.server.plugins.di.DependencyKey
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.InternalAPI
import kotlin.reflect.KType

/**
 * Returns a list of all supertypes and implemented interfaces, recursively iterating up the tree.
 *
 * When type parameters are encountered, they will be automatically substituted with the concrete values supplied to
 * the root [TypeInfo], if available.
 */
@InternalAPI
public expect fun TypeInfo.hierarchy(): List<TypeInfo>

/**
 * Provides the nullable version of the provided KType, or null if this is already nullable.
 */
@InternalAPI
public expect fun KType.toNullable(): KType?
