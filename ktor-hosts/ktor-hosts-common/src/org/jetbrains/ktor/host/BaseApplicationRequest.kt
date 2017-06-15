package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.reflect.*

abstract class BaseApplicationRequest() : ApplicationRequest {
    override val pipeline = ApplicationReceivePipeline().apply {
        intercept(ApplicationReceivePipeline.Transform) { query ->
            val value = query.value as? ApplicationRequest ?: return@intercept
            val transformed: Any? = when (query.type) {
                ReadChannel::class -> getReadChannel()
                InputStream::class -> getInputStream()
                MultiPartData::class -> getMultiPartData()
                String::class -> getReadChannel().readText()
                ValuesMap::class -> when {
                    contentType().match(ContentType.Application.FormUrlEncoded) -> {
                        val string = getReadChannel().readText()
                        parseQueryString(string)
                    }
                    contentType().match(ContentType.MultiPart.FormData) -> {
                        val items = getMultiPartData().parts.filterIsInstance<PartData.FormItem>()
                        ValuesMap.build {
                            items.forEach {
                                it.partName?.let { name -> append(name, it.value) }
                            }
                        }
                    }
                    else -> null // Respond UnsupportedMediaType? but what if someone else later would like to do it?
                }
                else -> null
            }
            if (transformed != null)
                proceedWith(ApplicationReceiveRequest(query.call, query.type, transformed))
        }
    }

    suspend fun ReadChannel.readText(): String {
        val buffer = ByteBufferWriteChannel()
        headers[HttpHeaders.ContentLength]?.toInt()?.let { contentLength ->
            buffer.ensureCapacity(contentLength)
        }

        copyTo(buffer) // TODO provide buffer pool to copyTo function
        return buffer.toByteArray().toString(contentCharset() ?: Charsets.ISO_8859_1)
    }

    protected abstract fun getReadChannel(): ReadChannel
    protected abstract fun getMultiPartData(): MultiPartData
    protected open fun getInputStream(): InputStream = getReadChannel().toInputStream()

    suspend override fun <T : Any> tryReceive(type: KClass<T>): T? {
        val transformed = pipeline.execute(ApplicationReceiveRequest(call, type, this)).value
        if (transformed is ApplicationRequest)
            return null

        @Suppress("UNCHECKED_CAST")
        return transformed as? T
    }
}