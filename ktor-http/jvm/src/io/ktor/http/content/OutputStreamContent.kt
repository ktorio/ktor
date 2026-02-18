/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

/**
 * [OutgoingContent] to respond with [OutputStream].
 * The stream would be automatically closed after [body] finish.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.content.OutputStreamContent)
 */
public class OutputStreamContent(
    private val body: suspend OutputStream.() -> Unit,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null,
    override val contentLength: Long? = null,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : OutgoingContent.WriteChannelContent() {

    @OptIn(InternalAPI::class)
    override suspend fun writeTo(channel: ByteWriteChannel) {
        val outputStream = ChannelOutputStream(channel, coroutineContext)
        try {
            outputStream.body()
        } finally {
            outputStream.closeSuspend()
        }
    }
}
