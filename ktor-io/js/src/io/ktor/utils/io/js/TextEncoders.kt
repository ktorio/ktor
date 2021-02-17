package io.ktor.utils.io.js

import org.khronos.webgl.*

internal external class TextEncoder() {
    val encoding: String

    public fun encode(input: String): Uint8Array
}
