package io.ktor.client.request.forms

import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.io.core.*
import kotlin.contracts.*

/**
 * Multipart form item. Use it to build form in client.
 *
 * @param key multipart name
 * @param value content, could be [String], [Number] or [Input]
 * @param headers part headers, note that some servers may fail if an unknown header provided
 */
data class FormPart<T : Any>(val key: String, val value: T, val headers: Headers = Headers.Empty)

/**
 * Build multipart form from [values].
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

/**
 * Build multipart form using [block] function
 */
fun formData(block: FormBuilder.() -> Unit): List<PartData> =
    formData(*FormBuilder().apply(block).build().toTypedArray())

/**
 * Form builder type used in [formData] builder function
 */
class FormBuilder internal constructor() {
    private val parts = mutableListOf<FormPart<*>>()

    /**
     * Append a pair [key]:[value] with optional [headers]
     */
    fun <T : Any> append(key: String, value: T, headers: Headers = Headers.Empty) {
        parts += FormPart(key, value, headers)
    }

    /**
     * Append a form [part]
     */
    fun <T : Any> append(part: FormPart<T>) {
        parts += part
    }

    internal fun build(): List<FormPart<*>> = parts
}

/**
 * Append a form part with the specified [key] using [bodyBuilder] for it's body
 */
@UseExperimental(ExperimentalContracts::class)
inline fun FormBuilder.append(key: String, headers: Headers = Headers.Empty, bodyBuilder: BytePacketBuilder.() -> Unit) {
    contract {
        callsInPlace(bodyBuilder, InvocationKind.EXACTLY_ONCE)
    }
    append(FormPart(key, buildPacket { bodyBuilder() }, headers))
}

/**
 * Append a form part with the specified [key], [filename] and optional [contentType] using [bodyBuilder] for it's body
 */
@UseExperimental(ExperimentalContracts::class)
fun FormBuilder.append(key: String, filename: String, contentType: ContentType? = null, bodyBuilder: BytePacketBuilder.() -> Unit) {
    contract {
        callsInPlace(bodyBuilder, InvocationKind.EXACTLY_ONCE)
    }

    val headersBuilder = HeadersBuilder()
    headersBuilder[HttpHeaders.ContentDisposition] ="filename=$filename"
    contentType?.run { headersBuilder[HttpHeaders.ContentType] = this.toString() }
    val headers = headersBuilder.build()

    append(key, headers, bodyBuilder)
}
