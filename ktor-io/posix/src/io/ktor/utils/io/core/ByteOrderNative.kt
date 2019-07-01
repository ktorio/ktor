package io.ktor.utils.io.core

import kotlinx.cinterop.*

actual enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN;

    actual companion object {
        private val native: ByteOrder

        init {
            native = memScoped {
                val i = alloc<IntVar>()
                i.value = 1
                val bytes = i.reinterpret<ByteVar>()
                if (bytes.value == 0.toByte()) BIG_ENDIAN else LITTLE_ENDIAN
            }
        }

        actual fun nativeOrder(): ByteOrder = native
    }
}
