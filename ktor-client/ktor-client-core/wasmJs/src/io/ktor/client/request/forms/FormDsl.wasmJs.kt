/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: WasmJS-specific FormBuilder extensions for appending browser Blob/File objects
// ABOUTME: to multipart form data requests.

package io.ktor.client.request.forms

import io.ktor.client.fetch.ArrayBuffer
import io.ktor.client.fetch.Blob
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * Appends a [Blob] part with the specified [key] and optional [headers].
 *
 * This enables sending browser Blob objects
 * as multipart form data parts using the Ktor client from Kotlin/WasmJS.
 *
 * Example:
 * ```kotlin
 * client.submitFormWithBinaryData(url, formData {
 *     appendBlob("document", blob)
 * })
 * ```
 *
 * @param key multipart field name
 * @param blob browser [Blob] payload to send
 * @param headers additional part headers; defaults to empty
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.forms.appendBlob)
 */
public fun FormBuilder.appendBlob(key: String, blob: Blob, headers: Headers = Headers.Empty) {
    append(key, blobChannelProvider(blob), headers)
}

/**
 * Appends a [Blob] part with the specified [key], [filename], and optional [contentType].
 *
 * This enables uploading browser Blob objects
 * with associated filename and content type metadata.
 *
 * Example:
 * ```kotlin
 * client.submitFormWithBinaryData(url, formData {
 *     appendBlob("document", blob, "report.pdf", ContentType.Application.Pdf)
 * })
 * ```
 *
 * @param key multipart field name
 * @param blob browser [Blob] payload to send
 * @param filename name to set in the Content-Disposition header
 * @param contentType optional content type for the part
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

@Suppress("UNUSED_PARAMETER")
private fun blobArrayBuffer(blob: Blob): Promise<ArrayBuffer> = js("blob.arrayBuffer()")

@Suppress("UNUSED_PARAMETER")
private fun createUint8Array(buffer: ArrayBuffer): Uint8Array = js("new Uint8Array(buffer)")

@OptIn(DelicateCoroutinesApi::class)
private fun blobChannelProvider(blob: Blob): ChannelProvider {
    val size = blob.size.toLong()
    return ChannelProvider(size) {
        GlobalScope.writer {
            val arrayBuffer = blobArrayBuffer(blob).await<ArrayBuffer>()
            val bytes = createUint8Array(arrayBuffer).asByteArray()
            channel.writeFully(bytes)
        }.channel
    }
}
