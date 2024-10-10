/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import kotlin.reflect.*

@Deprecated("Use KType instead.")
public actual typealias Type = KType

@Suppress("unused", "UNUSED_PARAMETER", "DEPRECATION")
@Deprecated("Maintained for binary compatibility.", level = DeprecationLevel.HIDDEN)
internal fun typeInfoImpl(reifiedType: Type, kClass: KClass<*>, kType: KType): TypeInfo = TypeInfo(kClass, kType)

/**
 * Check [this] is instance of [type].
 */
public actual fun Any.instanceOf(type: KClass<*>): Boolean = type.isInstance(this)

@Suppress("DEPRECATION")
@Deprecated("Use KType directly instead.", ReplaceWith("this"))
public actual val KType.platformType: Type
    get() = this
