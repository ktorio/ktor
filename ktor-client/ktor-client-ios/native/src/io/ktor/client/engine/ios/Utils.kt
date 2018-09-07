package io.ktor.client.engine.ios

import io.ktor.util.KtorExperimentalAPI
import kotlinx.cinterop.*
import platform.Foundation.*

@KtorExperimentalAPI
fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    if (isEmpty()) return@apply
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.toULong())
    }
}

@KtorExperimentalAPI
fun NSData.toByteArray(): ByteArray {
    val data: CPointer<ByteVar> = bytes!!.reinterpret()
    return ByteArray(length.toInt()) { index -> data[index] }
}

@KtorExperimentalAPI
class IosHttpRequestException(val origin: NSError? = null) : Throwable("Exception in http request: $origin")
