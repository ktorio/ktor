/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.streams.asByteWriteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okio.*
import kotlin.coroutines.CoroutineContext

internal class StreamAdapterIOException(cause: Throwable) : IOException(cause)

internal class StreamRequestBody(
    private val callContext: CoroutineContext,
    private val contentLength: Long?,
    private val duplex: Boolean,
    private val block: () -> ByteReadChannel
) : RequestBody() {

    override fun contentType(): MediaType? = null

    override fun writeTo(sink: BufferedSink) {
        if (duplex) {
            CoroutineScope(callContext).launch(Dispatchers.IO) {
                try {
                    val channel = block()
                    channel.copyTo(sink.outputStream().asByteWriteChannel())
                } catch (cause: IOException) {
                    throw cause
                } catch (cause: Throwable) {
                    throw StreamAdapterIOException(cause)
                }
            }
        } else {
            try {
                block().toInputStream().source().use {
                    sink.writeAll(it)
                }
            } catch (cause: IOException) {
                throw cause
            } catch (cause: Throwable) {
                throw StreamAdapterIOException(cause)
            }
        }
    }

    override fun contentLength(): Long = contentLength ?: -1

    override fun isOneShot(): Boolean = true

    override fun isDuplex(): Boolean = duplex
}
