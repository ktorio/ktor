package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import java.io.*
import kotlin.reflect.*

abstract class RequestContent(private val request: ApplicationRequest) {
    @Deprecated("Use getReadChannel instead")
    protected abstract fun getInputStream(): InputStream
    protected abstract fun getReadChannel(): AsyncReadChannel
    protected abstract fun getMultiPartData(): MultiPartData

    @Suppress("UNCHECKED_CAST")
    open operator fun <T : Any> get(type: KClass<T>): T {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        return when (type) {
            AsyncReadChannel::class -> getReadChannel()
            InputStream::class -> getInputStream()
            String::class -> getReadChannel().asInputStream().reader(request.contentCharset() ?: Charsets.ISO_8859_1).readText()
            MultiPartData::class -> getMultiPartData()
            else -> throw UnknownContentAccessorRequest("Requested content accessor '$type' cannot be provided")
        } as T
    }

    inline fun <reified T : Any> get(): T = get(T::class)
}

class UnknownContentAccessorRequest(message: String) : Exception(message)
