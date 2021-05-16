/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.sessions

import kotlin.reflect.*

/**
 * Creates the default [SessionSerializer] by [typeInfo]
 */
//TODO implement reflection less default session serializer for MPP
public expect fun <T : Any> defaultSessionSerializer(typeInfo: KType): SessionSerializer<T>
