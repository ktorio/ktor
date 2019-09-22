package io.ktor.utils.io.core

actual enum class ByteOrder(val nioOrder: java.nio.ByteOrder) {
    BIG_ENDIAN(java.nio.ByteOrder.BIG_ENDIAN),
    LITTLE_ENDIAN(java.nio.ByteOrder.LITTLE_ENDIAN);

    actual companion object {
        private val native: ByteOrder = orderOf(java.nio.ByteOrder.nativeOrder())
        fun of(nioOrder: java.nio.ByteOrder): ByteOrder = orderOf(nioOrder)

        actual fun nativeOrder(): ByteOrder = native
    }
}

private fun orderOf(nioOrder: java.nio.ByteOrder): ByteOrder = if (nioOrder === java.nio.ByteOrder.BIG_ENDIAN) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
