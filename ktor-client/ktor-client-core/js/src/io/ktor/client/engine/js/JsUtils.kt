/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.client.engine.js.node.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import org.khronos.webgl.*
import org.w3c.fetch.*
import kotlin.coroutines.*

internal suspend fun HttpRequestData.toRaw(callContext: CoroutineContext): RequestInit {
    val jsHeaders = if (PlatformUtils.IS_BROWSER) {
        Headers()
    } else {
        NodeFetch.Headers()
    }
    mergeHeaders(headers, body) { key, value ->
        jsHeaders.set(key, value)
    }

    val bodyBytes = when (val content = body) {
        is OutgoingContent.ByteArrayContent -> content.bytes()
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes()
        is OutgoingContent.WriteChannelContent -> {
            GlobalScope.writer(callContext) {
                content.writeTo(channel)
            }.channel.readRemaining().readBytes()
        }
        else -> null
    }?.let { Uint8Array(it.toTypedArray()) }

    return RequestInit(
        method = method.value,
        headers = jsHeaders,
        body = bodyBytes,
        redirect = RequestRedirect.FOLLOW
    )
}
