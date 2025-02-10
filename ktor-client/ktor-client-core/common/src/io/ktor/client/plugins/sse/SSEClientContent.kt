/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

@InternalAPI
public class SSEClientContent(
    public val reconnectionTime: Duration,
    public val showCommentEvents: Boolean,
    public val showRetryEvents: Boolean,
    public val maxReconnectionAttempts: Int,
    public val callContext: CoroutineContext,
    public val initialRequest: HttpRequestBuilder,
    requestBody: OutgoingContent
) : OutgoingContent.ContentWrapper(requestBody) {

    override val headers: Headers = HeadersBuilder().apply {
        appendAll(requestBody.headers)

        append(HttpHeaders.Accept, ContentType.Text.EventStream)
        append(HttpHeaders.CacheControl, "no-store")
    }.build()

    override fun toString(): String = "SSEClientContent"

    override fun copy(delegate: OutgoingContent): SSEClientContent {
        return SSEClientContent(
            reconnectionTime,
            showCommentEvents,
            showRetryEvents,
            maxReconnectionAttempts,
            callContext,
            initialRequest,
            delegate
        )
    }
}
