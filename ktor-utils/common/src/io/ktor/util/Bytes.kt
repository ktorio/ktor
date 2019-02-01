package io.ktor.util

/**
 * Read [Short] with specified [offset] from [ByteArray].
 */
@InternalAPI
fun ByteArray.readShort(offset: Int): Short {
    val result = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
    return result.toShort()
}
