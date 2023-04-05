/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import okhttp3.*
import okio.*

internal class StreamAdapterIOException(cause: Throwable) : IOException(cause)

internal class StreamRequestBody(
    private val contentLength: Long?,
    private val block: () -> ByteReadChannel
) : RequestBody() {

    override fun contentType(): MediaType? = null

    override fun writeTo(sink: BufferedSink) {
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

    override fun contentLength(): Long = contentLength ?: -1

    override fun isOneShot(): Boolean = true
}
