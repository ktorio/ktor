/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.sessions.serialization.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.*

/**
 * Creates the default [SessionSerializer] for the type [T].
 */
@Suppress("UNCHECKED_CAST")
public actual fun <T : Any> defaultSessionSerializer(typeInfo: KType): SessionSerializer<T> =
    KotlinxSessionSerializer(serializer(typeInfo) as KSerializer<T>, Json)
