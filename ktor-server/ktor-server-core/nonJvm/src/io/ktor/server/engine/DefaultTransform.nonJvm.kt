/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.charsets.*
import kotlinx.io.*

internal actual suspend fun PipelineContext<Any, PipelineCall>.defaultPlatformTransformations(
    query: Any
): Any? = null

internal actual fun Source.readTextWithCustomCharset(charset: Charset): String =
    error("Charset $charset is not supported on non JVM platforms")
