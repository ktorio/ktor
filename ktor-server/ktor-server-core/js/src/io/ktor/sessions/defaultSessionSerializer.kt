/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sessions

import kotlin.reflect.*

/**
 * Creates the default [SessionSerializer] by [typeInfo]
 */
@Suppress("DEPRECATION")
public actual fun <T : Any> defaultSessionSerializer(typeInfo: KType): SessionSerializer<T> {
    TODO("Not yet implemented")
}
