package io.ktor.utils.io.core

expect enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN;

    companion object {
        fun nativeOrder(): ByteOrder
    }
}

