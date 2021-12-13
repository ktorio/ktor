// ktlint-disable filename
package io.ktor.utils.io.core

public actual enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN;

    public actual companion object {
        private val native: ByteOrder = if (Platform.isLittleEndian) LITTLE_ENDIAN else BIG_ENDIAN

        public actual fun nativeOrder(): ByteOrder = native
    }
}
