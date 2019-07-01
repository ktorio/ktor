package io.ktor.utils.io.js

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import org.khronos.webgl.*

@Deprecated("Use readText with charset instead", ReplaceWith("readText(Charset.forName(encoding), max)", "kotlinx.io.core.readText", "kotlinx.io.charsets.Charset"))
fun ByteReadPacket.readText(encoding: String, max: Int = Int.MAX_VALUE): String = readText(Charset.forName(encoding), max)

@Deprecated("Use readText with charset instead", ReplaceWith("readText(out, Charset.forName(encoding), max)", "kotlinx.io.core.readText", "kotlinx.io.charsets.Charset"))
fun ByteReadPacket.readText(encoding: String = "UTF-8", out: Appendable, max: Int = Int.MAX_VALUE): Int {
    return readText(out, Charset.forName(encoding), max)
}

internal external class TextDecoder(encoding: String, options: dynamic = definedExternally) {
    val encoding: String

    fun decode(): String
    fun decode(buffer: ArrayBuffer): String
    fun decode(buffer: ArrayBuffer, options: dynamic): String
    fun decode(buffer: ArrayBufferView): String
    fun decode(buffer: ArrayBufferView, options: dynamic): String
}

private val STREAM_TRUE = Any().apply {
    with(this.asDynamic()) {
        stream = true
    }
}

private val FATAL_TRUE = Any().apply {
    with(this.asDynamic()) {
        fatal = true
    }
}

internal fun TextDecoderFatal(encoding: String, fatal: Boolean = true): TextDecoder {
    // PhantomJS does not support TextDecoder yet so we use node module text-encoding for tests
    if (js("typeof TextDecoder") == "undefined") {
        val module = js("require('text-encoding')")
        if (module.TextDecoder === undefined) throw IllegalStateException("TextDecoder is not supported by your browser and no text-encoding module found")
        val ctor = module.TextDecoder
        val objPrototype = js("Object").create(ctor.prototype)

        @Suppress("UnsafeCastFromDynamic")
        return if (fatal) ctor.call(objPrototype, encoding, FATAL_TRUE)
        else ctor.call(objPrototype, encoding)
    }

    return if (fatal) TextDecoder(encoding, FATAL_TRUE) else TextDecoder(encoding)
}

internal inline fun TextDecoder.decodeStream(buffer: ArrayBufferView, stream: Boolean): String {
    decodeWrap {
        return if (stream) {
            decode(buffer, STREAM_TRUE)
        } else {
            decode(buffer)
        }
    }
}

internal inline fun TextDecoder.decodeStream(buffer: ArrayBuffer, stream: Boolean): String {
    decodeWrap {
        return if (stream) {
            decode(buffer, STREAM_TRUE)
        } else {
            decode(buffer)
        }
    }
}

internal inline fun <R> decodeWrap(block: () -> R): R {
    try {
        return block()
    } catch (t: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${t.message ?: "no cause provided"}")
    }
}
