package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.reflect.*

abstract class BaseApplicationRequest() : ApplicationRequest {
    private val contentAsString by lazy { getReadChannel().toInputStream().reader(contentCharset() ?: Charsets.ISO_8859_1).readText() }

    private val computedValuesMap: ValuesMap by lazy {
        when {
            contentType().match(ContentType.Application.FormUrlEncoded) -> {
                parseQueryString(contentAsString)
            }
            contentType().match(ContentType.MultiPart.FormData) -> {
                ValuesMap.build {
                    getMultiPartData().parts.filterIsInstance<PartData.FormItem>().forEach { part ->
                        part.partName?.let { name ->
                            append(name, part.value)
                        }
                    }
                }
            }
            else -> ValuesMap.Empty
        }
    }

    protected abstract fun getReadChannel(): ReadChannel
    protected abstract fun getMultiPartData(): MultiPartData
    protected open fun getInputStream(): InputStream = getReadChannel().toInputStream()

    suspend override fun <T : Any> receive(type: KClass<T>): T {
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
}