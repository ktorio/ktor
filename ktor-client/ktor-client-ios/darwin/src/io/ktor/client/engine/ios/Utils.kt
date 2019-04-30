/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.util.KtorExperimentalAPI
import kotlinx.cinterop.*
import platform.Foundation.*

@KtorExperimentalAPI
internal fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (isEmpty()) return@apply
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.convert())
    }
}

@KtorExperimentalAPI
internal fun NSData.toByteArray(): ByteArray {
    val data: CPointer<ByteVar> = bytes!!.reinterpret()
    return ByteArray(length.toInt()) { index -> data[index] }
}

@KtorExperimentalAPI
@Suppress("KDocMissingDocumentation")
class IosHttpRequestException(val origin: NSError? = null) : Throwable("Exception in http request: $origin")
