/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import io.ktor.server.cio.backend.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

@Deprecated("This is going to become private", level = DeprecationLevel.HIDDEN)
@Suppress("KDocMissingDocumentation", "unused")
public fun lastHttpRequest(http11: Boolean, connectionOptions: ConnectionOptions?): Boolean {
    return isLastHttpRequest(http11, connectionOptions)
}

/**
 * HTTP request handler function
 */
public typealias HttpRequestHandler = suspend ServerRequestScope.(
    request: Request
) -> Unit

/**
 * HTTP pipeline coroutine name
 */
@Deprecated("This is an implementation detail and will become internal in future releases.")
public val HttpPipelineCoroutine: CoroutineName = CoroutineName("http-pipeline")

/**
 * HTTP pipeline writer coroutine name
 */
@Deprecated("This is an implementation detail and will become internal in future releases.")
public val HttpPipelineWriterCoroutine: CoroutineName = CoroutineName("http-pipeline-writer")

/**
 * HTTP request handler coroutine name
 */
@Deprecated("This is an implementation detail and will become internal in future releases.")
public val RequestHandlerCoroutine: CoroutineName = CoroutineName("request-handler")

/**
 * Start connection HTTP pipeline invoking [handler] for every request.
 * Note that [handler] could be invoked multiple times concurrently due to HTTP pipeline nature
 *
 * @param input incoming channel
 * @param output outgoing bytes channel
 * @param timeout number of IDLE seconds after the connection will be closed
 * @param handler to be invoked for every incoming request
 *
 * @return pipeline job
 */
@Deprecated(
    "This is going to become internal. " +
        "Start ktor server or raw cio server from ktor-server-cio module instead of constructing server from parts."
)
public fun CoroutineScope.startConnectionPipeline(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    timeout: WeakTimeoutQueue,
    handler: suspend CoroutineScope.(
        request: Request,
        input: ByteReadChannel,
        output: ByteWriteChannel,
        upgraded: CompletableDeferred<Boolean>?
    ) -> Unit
): Job {
    val pipeline = ServerIncomingConnection(input, output, null, null)
    return startServerConnectionPipeline(pipeline, timeout) { request ->
        handler(this, request, input, output, upgraded)
    }
}
