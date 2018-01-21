package io.ktor.server.engine

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import java.io.*

fun ApplicationSendPipeline.installDefaultTransformations() {
    intercept(ApplicationSendPipeline.Render) { value ->
        val transformed = transformDefaultContent(value)
        if (transformed != null)
            proceedWith(transformed)
    }
}

fun ApplicationReceivePipeline.installDefaultTransformations() {
    intercept(ApplicationReceivePipeline.Transform) { query ->
        val value = query.value as? IncomingContent ?: return@intercept
        val transformed: Any? = when (query.type) {
            ByteReadChannel::class -> value.readChannel()
            InputStream::class -> value.inputStream()
            MultiPartData::class -> value.multiPartData()
            String::class -> value.readText()
            Parameters::class -> {
                val contentType = value.contentType()
                when {
                    contentType?.match(ContentType.Application.FormUrlEncoded) == true -> {
                        val string = value.readText()
                        parseQueryString(string)
                    }
                    contentType?.match(ContentType.MultiPart.FormData) == true -> {
                        Parameters.build {
                            val multipart = value.multiPartData()
                            while (true) {
                                val it = multipart.readPart() ?: break
                                if (it is PartData.FormItem) {
                                    it.partName?.let { name -> append(name, it.value) }
                                } else {
                                    it.dispose()
                                }
                            }
                        }
                    }
                    else -> null // Respond UnsupportedMediaType? but what if someone else later would like to do it?
                }
            }
            else -> null
        }
        if (transformed != null)
            proceedWith(ApplicationReceiveRequest(query.type, transformed))
    }
}

