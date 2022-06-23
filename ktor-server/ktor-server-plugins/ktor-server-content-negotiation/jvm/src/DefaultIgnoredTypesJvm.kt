/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.utils.io.*
import java.io.*
import kotlin.reflect.*

internal actual val DefaultIgnoredTypes: Set<KClass<*>> =
    mutableSetOf(HttpStatusCode::class, ByteReadChannel::class, InputStream::class)
