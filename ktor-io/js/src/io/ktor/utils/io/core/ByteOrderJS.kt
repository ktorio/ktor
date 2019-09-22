package io.ktor.utils.io.core

import org.khronos.webgl.*

actual enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN;

    actual companion object {
        private val native: ByteOrder

        init {
            val buffer = ArrayBuffer(4)
            val arr = Int32Array(buffer)
            val view = DataView(buffer)

            arr[0] = 0x11223344

            native = if (view.getInt32(0, true) == 0x11223344) LITTLE_ENDIAN else BIG_ENDIAN
        }

        actual fun nativeOrder(): ByteOrder = native
    }
}
