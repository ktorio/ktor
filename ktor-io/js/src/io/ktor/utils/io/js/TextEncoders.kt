package io.ktor.utils.io.js

import org.khronos.webgl.*

internal external class TextEncoder() {
    val encoding: String

    fun encode(input: String): Uint8Array
}

internal fun TextEncoderCtor(): TextEncoder {
    // PhantomJS does not support TextEncoder yet so we use node module text-encoding for tests
    if (js("typeof TextEncoder") == "undefined") {
        val module = js("require('text-encoding')")
        if (module.TextEncoder === undefined) throw IllegalStateException("TextEncoder is not supported by your browser and no text-encoding module found")
        val ctor = module.TextEncoder
        val objPrototype = js("Object").create(ctor.prototype)

        @Suppress("UnsafeCastFromDynamic")
        return ctor.call(objPrototype)
    }

    return TextEncoder()
}
