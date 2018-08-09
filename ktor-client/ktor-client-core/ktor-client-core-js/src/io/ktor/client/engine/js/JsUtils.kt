package io.ktor.client.engine.js

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import org.khronos.webgl.*
import org.w3c.fetch.*
import kotlin.browser.*
import kotlin.coroutines.*

internal suspend fun HttpRequest.toRaw(): RequestInit {
    val jsHeaders = js("({})")
    headers.forEach { key, values ->
        jsHeaders[key] = values
    }

    val content = content
    val bodyBytes = when (content) {
        is OutgoingContent.ByteArrayContent -> content.bytes()
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes()
        is OutgoingContent.WriteChannelContent -> writer(coroutineContext) { content.writeTo(channel) }
            .channel.readRemaining().readBytes()
        else -> null
    }

    return buildObject {
        method = this@toRaw.method.value
        headers = jsHeaders

        bodyBytes?.let { body = Uint8Array(it.toTypedArray()) }
    }
}

internal suspend fun fetch(url: Url, request: RequestInit): Response = suspendCancellableCoroutine {
    window.fetch(url.toString(), request).then({ response ->
        it.resume(response)
    }, { cause ->
        it.resumeWithException(cause)
    })
}

internal fun <T> buildObject(block: T.() -> Unit): T = (js("{}") as T).apply(block)
