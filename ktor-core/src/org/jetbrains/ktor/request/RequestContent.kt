package org.jetbrains.ktor.request

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.reflect.*

abstract class RequestContent(private val request: ApplicationRequest) {
    private val contentAsString by lazy { get<ReadChannel>().toInputStream().reader(request.contentCharset() ?: Charsets.ISO_8859_1).readText() }
    private val computedValuesMap: ValuesMap by lazy {
        if (request.contentType().match(ContentType.Application.FormUrlEncoded)) {
            parseQueryString(get<String>())
        } else if (request.contentType().match(ContentType.MultiPart.FormData)) {
            ValuesMap.build {
                get<MultiPartData>().parts.filterIsInstance<PartData.FormItem>().forEach { part ->
                    part.partName?.let { name ->
                        append(name, part.value)
                    }
                }
            }
        } else {
            ValuesMap.Empty
        }
    }

    protected abstract fun getInputStream(): InputStream
    protected abstract fun getReadChannel(): ReadChannel
    protected abstract fun getMultiPartData(): MultiPartData

    open operator fun <T : Any> get(type: KClass<T>): T {
        @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
        return when (type) {
            ReadChannel::class -> getReadChannel()
            InputStream::class -> getInputStream()
            String::class -> contentAsString
            ValuesMap::class -> computedValuesMap
            MultiPartData::class -> getMultiPartData()
            else -> throw UnknownContentAccessorRequest("Requested content accessor '$type' cannot be provided")
        } as T
    }

    inline fun <reified T : Any> get(): T = get(T::class)
}

class UnknownContentAccessorRequest(message: String) : Exception(message)
