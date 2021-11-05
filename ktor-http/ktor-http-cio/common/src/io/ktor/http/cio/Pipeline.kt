/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.cio.backend.*
import io.ktor.http.cio.internals.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.native.concurrent.*

/**
 * HTTP request handler function
 */
public typealias HttpRequestHandler = suspend ServerRequestScope.(request: Request) -> Unit

/**
 * HTTP pipeline coroutine name
 */
@Deprecated(
    "This is an implementation detail and will become internal in future releases.",
    level = DeprecationLevel.ERROR
)
@SharedImmutable
public val HttpPipelineCoroutine: CoroutineName = CoroutineName("http-pipeline")

/**
 * HTTP pipeline writer coroutine name
 */
@Deprecated(
    "This is an implementation detail and will become internal in future releases.",
    level = DeprecationLevel.ERROR
)
@SharedImmutable
public val HttpPipelineWriterCoroutine: CoroutineName = CoroutineName("http-pipeline-writer")

/**
 * HTTP request handler coroutine name
 */
@Deprecated(
    "This is an implementation detail and will become internal in future releases.",
    level = DeprecationLevel.ERROR
)
@SharedImmutable
public val RequestHandlerCoroutine: CoroutineName = CoroutineName("request-handler")
