package io.ktor.client.request.forms

import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.io.core.*

/**
 * Multipart form item. Use it to build form in client
 * [FormPart.value] could be [String], [Number] or [Input]
 */
data class FormPart<T : Any>(val key: String, val value: T, val headers: Headers = Headers.Empty)

/**
 * Build form from [values].
 */
fun formData(vararg values: FormPart<*>): List<PartData> {
    val result = mutableListOf<PartData>()

    values.forEach { (key, value, headers) ->
        val partHeaders = Headers.build {
            append(HttpHeaders.ContentDisposition, "form-data;name=$key")
            appendAll(headers)
        }
        val part = when (value) {
            is String -> PartData.FormItem(value, {}, partHeaders)
            is Number -> PartData.FormItem(value.toString(), {}, partHeaders)
            is ByteArray -> PartData.BinaryItem({ buildPacket { writeFully(value) } }, {}, partHeaders)
            is Input -> PartData.BinaryItem({ value }, { }, partHeaders)
            else -> throw error("Unknown form content type: $value")
        }

        result += part
    }

    return result
}

fun formData(block: FormBuilder.() -> Unit): List<PartData> =
    formData(*FormBuilder().apply(block).build().toTypedArray())

class FormBuilder internal constructor() {
    private val parts = mutableListOf<FormPart<*>>()

    fun <T : Any> append(key: String, value: T, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    fun <T : Any> append(part: FormPart<T>) {
        parts += part
    }

    inline fun append(key: String, headers: Headers = Headers.Empty, block: BytePacketBuilder.() -> Unit) {
        append(FormPart(key, buildPacket { block() }, headers))
    }

    fun append(key: String, filename: String, block: BytePacketBuilder.() -> Unit) {
        val filenameHeader: Headers = headersOf(
            HttpHeaders.ContentDisposition, "filename=$filename"
        )
        append(key, filenameHeader, block)
    }

    internal fun build(): List<FormPart<*>> = parts
}
