/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.fetch.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.readByteArray
import org.khronos.webgl.Uint8Array
import org.w3c.dom.events.Event
import org.w3c.fetch.FOLLOW
import org.w3c.fetch.MANUAL
import org.w3c.fetch.RequestRedirect
import kotlin.coroutines.CoroutineContext

// Prefixed to avoid a name collision with Vue's `toRaw` export when bundled by Vite (KTOR-9549).
@JsName("ktor_toRaw")
@OptIn(InternalAPI::class)
internal suspend fun HttpRequestData.toRaw(
    clientConfig: HttpClientConfig<*>,
    callContext: CoroutineContext
): RequestInit {
    val jsHeaders = js("({})")
    forEachHeader { key, value -> jsHeaders[key] = value }

    val bodyBytes = getBodyBytes(body, callContext)

    return buildObject {
        method = this@toRaw.method.value
        headers = jsHeaders
        redirect = if (clientConfig.followRedirects) RequestRedirect.FOLLOW else RequestRedirect.MANUAL

        bodyBytes?.let { body = Uint8Array(it.toTypedArray()) }
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

internal fun <T> buildObject(block: T.() -> Unit): T = (js("{}") as T).apply(block)

internal actual fun Event.asString(): String = JSON.stringify(this, arrayOf("message", "target", "type", "isTrusted"))
