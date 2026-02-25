/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: JS-specific FormBuilder extensions for appending browser Blob/File objects
// ABOUTME: to multipart form data requests.

package io.ktor.client.request.forms

import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.files.Blob
import kotlin.js.Promise

/**
 * Appends a [Blob] part with the specified [key] and optional [headers].
 *
 * This enables sending browser [File][org.w3c.files.File] and [Blob] objects
 * as multipart form data parts using the Ktor client from Kotlin/JS.
 *
 * Example:
 * ```kotlin
 * val file: File = // from <input type="file">
 * client.submitFormWithBinaryData(url, formData {
 *     appendBlob("document", file)
 * })
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.forms.appendBlob)
 */
public fun FormBuilder.appendBlob(key: String, blob: Blob, headers: Headers = Headers.Empty) {
    append(key, blobChannelProvider(blob), headers)
}

/**
 * Appends a [Blob] part with the specified [key], [filename], and optional [contentType].
 *
 * This enables uploading browser [File][org.w3c.files.File] and [Blob] objects
 * with associated filename and content type metadata.
 *
 * Example:
 * ```kotlin
 * val file: File = // from <input type="file">
 * client.submitFormWithBinaryData(url, formData {
 *     appendBlob("document", file, file.name, ContentType.Application.Pdf)
 * })
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.forms.appendBlob)
 */
public fun FormBuilder.appendBlob(
    key: String,
    blob: Blob,
    filename: String,
    contentType: ContentType? = null
) {
    val headersBuilder = HeadersBuilder()
    headersBuilder[HttpHeaders.ContentDisposition] = "filename=${filename.escapeIfNeeded()}"
    contentType?.let { headersBuilder[HttpHeaders.ContentType] = it.toString() }

    append(key, blobChannelProvider(blob), headersBuilder.build())
}

@OptIn(DelicateCoroutinesApi::class)
private fun blobChannelProvider(blob: Blob): ChannelProvider {
    val size = blob.size.toLong()
    return ChannelProvider(size) {
        GlobalScope.writer {
            val arrayBuffer = blob.asDynamic().arrayBuffer().unsafeCast<Promise<ArrayBuffer>>().await()
            val bytes = Int8Array(arrayBuffer).unsafeCast<ByteArray>()
            channel.writeFully(bytes)
        }.channel
    }
}
