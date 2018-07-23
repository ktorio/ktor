package io.ktor.client.engine.ios

import kotlinx.cinterop.*
import platform.Foundation.*

fun ByteArray.toNSData(): NSData = NSMutableData().apply {
    this@toNSData.usePinned {
        appendBytes(it.addressOf(0), size.toLong())
    }
}

fun NSData.toByteArray(): ByteArray {
    val data: CPointer<ByteVar> = bytes!!.reinterpret()
    return ByteArray(length.toInt()) { index -> data[index] }
}

internal fun String.encode(encoding: NSStringEncoding = NSWindowsCP1251StringEncoding): NSData =
    (this as NSString).dataUsingEncoding(encoding)!!

internal fun NSData.decode(encoding: NSStringEncoding = NSWindowsCP1251StringEncoding): String =
    NSString.create(this, encoding) as String

class IosHttpRequestException(val origin: NSError? = null) : Throwable("Exception in http request: $origin")
