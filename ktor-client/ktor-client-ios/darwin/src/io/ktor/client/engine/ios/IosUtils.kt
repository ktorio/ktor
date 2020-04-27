/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.client.call.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*

internal suspend fun OutgoingContent.toNSData(): NSData? = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().toNSData()
    is OutgoingContent.WriteChannelContent -> GlobalScope.writer(Dispatchers.Unconfined) {
        writeTo(channel)
    }.channel.readRemaining().readBytes().toNSData()
    is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readBytes().toNSData()
    is OutgoingContent.NoContent -> null
    else -> throw UnsupportedContentTypeException(this)
}

internal fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (isEmpty()) return@apply
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.convert())
    }
}

internal fun NSData.toByteArray(): ByteArray {
    val data: CPointer<ByteVar> = bytes!!.reinterpret()
    return ByteArray(length.toInt()) { index -> data[index] }
}

@KtorExperimentalAPI
@Suppress("KDocMissingDocumentation")
class IosHttpRequestException(val origin: NSError) : Throwable("Exception in http request: $origin")
