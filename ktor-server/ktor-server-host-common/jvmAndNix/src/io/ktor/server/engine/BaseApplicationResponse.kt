/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Base class for implementing an [ApplicationResponse]
 */
public expect abstract class BaseApplicationResponse(call: ApplicationCall) : ApplicationResponse {

    final override val call: ApplicationCall
    final override val pipeline: ApplicationSendPipeline
    override val cookies: ResponseCookies

    override fun status(): HttpStatusCode?
    override fun status(value: HttpStatusCode)

    @UseHttp2Push
    override fun push(builder: ResponsePushBuilder)

    /**
     * Commit header values and status and pass them to the underlying engine
     */
    protected fun commitHeaders(content: OutgoingContent)

    /**
     * Process response outgoing [content]
     */
    protected open suspend fun respondOutgoingContent(content: OutgoingContent)

    /**
     * Process response with no content
     */
    protected open suspend fun respondNoContent(content: OutgoingContent.NoContent)

    /**
     * Process response [content] using [OutgoingContent.WriteChannelContent.writeTo].
     */
    protected open suspend fun respondWriteChannelContent(content: OutgoingContent.WriteChannelContent)

    /**
     * Respond with [bytes] content
     */
    protected open suspend fun respondFromBytes(bytes: ByteArray)

    /**
     * Respond from [readChannel]
     */
    protected open suspend fun respondFromChannel(readChannel: ByteReadChannel)

    /**
     * Process upgrade response
     */
    protected abstract suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade)

    /**
     * Get response output channel
     */
    protected abstract suspend fun responseChannel(): ByteWriteChannel

    /**
     * Set underlying engine's response status
     */
    protected abstract fun setStatus(statusCode: HttpStatusCode)

    /**
     * Thrown when there was already response sent but we are trying to respond again
     */
    public class ResponseAlreadySentException : IllegalStateException

    /**
     * [OutgoingContent] is trying to set some header that is not allowed for this content type.
     * For example, only upgrade content can set `Upgrade` header.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public class InvalidHeaderForContent(
        name: String,
        content: String
    ) : IllegalStateException, CopyableThrowable<InvalidHeaderForContent>

    /**
     * Content's actual body size doesn't match the provided one in `Content-Length` header
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public class BodyLengthIsTooSmall(
        expected: Long,
        actual: Long
    ) : IllegalStateException,
        CopyableThrowable<BodyLengthIsTooSmall>

    @OptIn(ExperimentalCoroutinesApi::class)
    public class BodyLengthIsTooLong(expected: Long) : IllegalStateException, CopyableThrowable<BodyLengthIsTooLong>

    public companion object {
        /**
         * Attribute key to access engine's response instance.
         * This is engine internal API and should be never used by end-users
         * unless you are writing your own engine implementation
         */
        public val EngineResponseAttributeKey: AttributeKey<BaseApplicationResponse>

        /**
         * Install an application-wide send pipeline interceptor into [ApplicationSendPipeline.Engine] phase
         * to start response object processing via [respondOutgoingContent]
         */
        public fun setupSendPipeline(sendPipeline: ApplicationSendPipeline)
    }
}
