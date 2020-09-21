package io.ktor.utils.io.core

public expect enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN;

    public companion object {
        public fun nativeOrder(): ByteOrder
    }
}

