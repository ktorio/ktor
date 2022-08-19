/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

@KtorDsl
@Suppress("UNUSED_PARAMETER", "DEPRECATION")
public class ClientPluginBuilder<PluginConfig : Any> internal constructor(
    internal val key: AttributeKey<ClientPluginInstance<PluginConfig>>,
    public val client: HttpClient,
    public val pluginConfig: PluginConfig
) {

    internal val hooks: MutableList<HookHandler<*>> = mutableListOf()
    internal var onClose: () -> Unit = {}

    public fun onRequest(
        block: suspend OnRequestContext.(request: HttpRequestBuilder, content: Any) -> Unit
    ) {
        on(RequestHook, block)
    }

    public fun onSendRequest(
        block: suspend OnSendRequestContext.(request: HttpRequestBuilder, content: OutgoingContent) -> Unit
    ) {
        on(SendRequestHook, block)
    }

    public fun onResponse(
        block: suspend OnResponseContext.(response: HttpResponse) -> Unit
    ) {
        on(ResponseHook, block)
    }

    public fun transformRequestBody(
        block: suspend TransformRequestBodyContext.(
            request: HttpRequestBuilder,
            content: Any,
            bodyType: TypeInfo?
        ) -> OutgoingContent?
    ) {
        on(TransformRequestBodyHook, block)
    }

    public fun transformResponseBody(
        block: suspend TransformResponseBodyContext.(
            response: HttpResponse,
            content: ByteReadChannel,
            requestedType: TypeInfo
        ) -> Any?
    ) {
        on(TransformResponseBodyHook, block)
    }

    public fun onClose(block: () -> Unit) {
        onClose = block
    }

    public fun <HookHandler> on(
        hook: ClientHook<HookHandler>,
        handler: HookHandler
    ) {
        hooks.add(HookHandler(hook, handler))
    }
}

private object RequestHook :
    ClientHook<suspend OnRequestContext.(request: HttpRequestBuilder, content: Any) -> Unit> {

    override fun install(
        client: HttpClient,
        handler: suspend OnRequestContext.(request: HttpRequestBuilder, content: Any) -> Unit
    ) {
        client.requestPipeline.intercept(HttpRequestPipeline.State) {
            handler(OnRequestContext(), context, subject)
        }
    }
}

private object ResponseHook :
    ClientHook<suspend OnResponseContext.(response: HttpResponse) -> Unit> {

    override fun install(
        client: HttpClient,
        handler: suspend OnResponseContext.(response: HttpResponse) -> Unit
    ) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) {
            handler(OnResponseContext(), subject)
        }
    }
}

private object SendRequestHook :
    ClientHook<suspend OnSendRequestContext.(request: HttpRequestBuilder, content: OutgoingContent) -> Unit> {

    override fun install(
        client: HttpClient,
        handler: suspend OnSendRequestContext.(request: HttpRequestBuilder, content: OutgoingContent) -> Unit
    ) {
        client.sendPipeline.intercept(HttpSendPipeline.State) {
            handler(OnSendRequestContext(), context, subject as OutgoingContent)
        }
    }
}

private object TransformRequestBodyHook :
    ClientHook<suspend TransformRequestBodyContext.(
        request: HttpRequestBuilder,
        content: Any,
        bodyType: TypeInfo?
    ) -> OutgoingContent?> {

    override fun install(
        client: HttpClient,
        handler: suspend TransformRequestBodyContext.(
            request: HttpRequestBuilder,
            content: Any,
            bodyType: TypeInfo?
        ) -> OutgoingContent?
    ) {
        client.requestPipeline.intercept(HttpRequestPipeline.Transform) {
            val newContent = handler(TransformRequestBodyContext(), context, subject, context.bodyType)
            if (newContent != null) proceedWith(newContent)
        }
    }
}

private object TransformResponseBodyHook :
    ClientHook<suspend TransformResponseBodyContext.(
        response: HttpResponse,
        content: ByteReadChannel,
        requestedType: TypeInfo
    ) -> Any?> {

    override fun install(
        client: HttpClient,
        handler: suspend TransformResponseBodyContext.(
            response: HttpResponse,
            content: ByteReadChannel,
            requestedType: TypeInfo
        ) -> Any?
    ) {
        client.responsePipeline.intercept(HttpResponsePipeline.Transform) {
            val (typeInfo, content) = subject
            if (content !is ByteReadChannel) return@intercept
            val newContent = handler(TransformResponseBodyContext(), context.response, content, typeInfo)
                ?: return@intercept
            if (newContent !is NullBody && !typeInfo.type.isInstance(newContent)) {
                throw IllegalStateException(
                    "transformResponseBody returned $newContent but expected value of type $typeInfo"
                )
            }
            proceedWith(HttpResponseContainer(typeInfo, newContent))
        }
    }
}

public object SetupRequest : ClientHook<suspend (HttpRequestBuilder) -> Unit> {
    override fun install(client: HttpClient, handler: suspend (HttpRequestBuilder) -> Unit) {
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            handler(context)
        }
    }
}

public object Send : ClientHook<suspend Sender.(HttpRequestBuilder) -> HttpClientCall> {
    override fun install(client: HttpClient, handler: suspend Sender.(HttpRequestBuilder) -> HttpClientCall) {
        client.plugin(HttpSend).intercept(handler)
    }
}
