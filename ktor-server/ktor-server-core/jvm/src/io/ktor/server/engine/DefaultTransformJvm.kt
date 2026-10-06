/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.job
import java.io.InputStream

@OptIn(InternalAPI::class)
internal actual suspend fun PipelineContext<Any, PipelineCall>.defaultPlatformTransformations(
    query: Any
): Any? {
    val channel = query as? ByteReadChannel ?: return null

    return when (call.receiveType.type) {
        InputStream::class -> channel.asInputStream(call.coroutineContext.job)
        else -> null
    }
}
