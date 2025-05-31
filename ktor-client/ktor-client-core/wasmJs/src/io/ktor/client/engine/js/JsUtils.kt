/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.fetch.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.readByteArray
import org.w3c.fetch.FOLLOW
import org.w3c.fetch.MANUAL
import org.w3c.fetch.RequestRedirect
import kotlin.coroutines.CoroutineContext

@OptIn(InternalAPI::class)
internal suspend fun HttpRequestData.toRaw(
    clientConfig: HttpClientConfig<*>,
    callContext: CoroutineContext
): RequestInit {
    val jsHeaders = makeJsObject<JsAny>().also { obj ->
        forEachHeader { key, value -> obj[key] = value }
    }

    val bodyBytes: ByteArray? = getBodyBytes(body, callContext)

    return makeJsObject<RequestInit>().also {
        it.method = this@toRaw.method.value
        it.headers = jsHeaders
        it.redirect = if (clientConfig.followRedirects) RequestRedirect.FOLLOW else RequestRedirect.MANUAL
        if (bodyBytes != null) {
            it.body = bodyBytes.asJsArray()
        }
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
