package io.ktor.network.tls.extensions

enum class PointFormat(val code: Byte) {
    UNCOMPRESSED(0),
    ANSIX962_COMPRESSED_PRIME(1),
    ANSIX962_COMPRESSED_CHAR2(2);
}

val SupportedPointFormats: List<PointFormat> = listOf(
    PointFormat.UNCOMPRESSED,
    PointFormat.ANSIX962_COMPRESSED_PRIME,
    PointFormat.ANSIX962_COMPRESSED_CHAR2
)
