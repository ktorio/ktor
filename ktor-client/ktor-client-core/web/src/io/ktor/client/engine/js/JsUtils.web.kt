/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.HttpMethod
import io.ktor.http.content.*
import io.ktor.utils.io.*
import js.array.jsArrayOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.readByteArray
import web.blob.Blob
import web.http.BodyInit
import web.http.DELETE
import web.http.GET
import web.http.HEAD
import web.http.Headers
import web.http.OPTIONS
import web.http.PATCH
import web.http.POST
import web.http.PUT
import web.http.RequestInit
import web.http.RequestMethod
import web.http.RequestRedirect
import web.http.follow
import web.http.manual
import kotlin.coroutines.CoroutineContext
import kotlin.js.JsAny
import kotlin.js.JsArray

@OptIn(InternalAPI::class)
internal suspend fun HttpRequestData.toRaw(
    clientConfig: HttpClientConfig<*>,
    callContext: CoroutineContext
): RequestInit {
    val jsHeaders = Headers()
    forEachHeader { key, value -> jsHeaders.set(key, value) }

    val bodyBytes: ByteArray? = getBodyBytes(body, callContext)

    return makeJsObject<RequestInit>().also {
        it.method = this@toRaw.method.mapMethod()
        it.headers = jsHeaders
        it.redirect = if (clientConfig.followRedirects) RequestRedirect.follow else RequestRedirect.manual
        if (bodyBytes != null) {
            val array = bodyBytes.asJsArray()
            it.body = BodyInit(Blob(jsArrayOf(array)))
        }
    }
}

private fun HttpMethod.mapMethod(): RequestMethod? {
    return when (this) {
        HttpMethod.Get -> RequestMethod.GET
        HttpMethod.Post -> RequestMethod.POST
        HttpMethod.Head -> RequestMethod.HEAD
        HttpMethod.Delete -> RequestMethod.DELETE
        HttpMethod.Options -> RequestMethod.OPTIONS
        HttpMethod.Patch -> RequestMethod.PATCH
        HttpMethod.Put -> RequestMethod.PUT
        else -> null
    }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun getBodyBytes(content: OutgoingContent, callContext: CoroutineContext): ByteArray? {
    return when (content) {
        is OutgoingContent.ByteArrayContent -> content.bytes()
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readByteArray()
        is OutgoingContent.WriteChannelContent -> {
            GlobalScope.writer(callContext) {
                content.writeTo(channel)
            }.channel.readRemaining().readByteArray()
        }
        is OutgoingContent.ContentWrapper -> getBodyBytes(content.delegate(), callContext)
        is OutgoingContent.NoContent -> null
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(content)
    }
}
