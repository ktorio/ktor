package org.jetbrains.ktor.gson

import com.google.gson.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class GsonSupport(val gson: Gson) {
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, GsonBuilder, GsonSupport> {
        override val key = AttributeKey<GsonSupport>("gson")

        override fun install(pipeline: ApplicationCallPipeline, configure: GsonBuilder.() -> Unit): GsonSupport {
            val gson = GsonBuilder().apply(configure).create()
            val feature = GsonSupport(gson)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) {
                if (it !is FinalContent && call.request.acceptItems().any { ContentType.Application.Json.match(it.value) }) {
                    proceedWith(feature.renderJsonContent(it))
                }
            }
            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                if (call.request.contentType().match(ContentType.Application.Json)) {
                    val message = it.value as? IncomingContent ?: return@intercept
                    val json = message.readText()
                    val value = gson.fromJson(json, it.type.javaObjectType)
                    proceedWith(ApplicationReceiveRequest(it.type, value))
                }
            }
            return feature
        }
    }

    private fun renderJsonContent(model: Any): JsonContent {
        val json = gson.toJson(model)
        return JsonContent(json, HttpStatusCode.OK)
    }
}

private class JsonContent(text: String, override val status: HttpStatusCode? = null) : FinalContent.ByteArrayContent() {
    private val bytes = text.toByteArray(Charsets.UTF_8)

    override val headers = ValuesMap.build(true) {
        set(HttpHeaders.ContentType, contentType)
        set(HttpHeaders.ContentLength, bytes.size.toString())
    }


    override fun bytes(): ByteArray = bytes
    override fun toString() = "JsonContent \"${bytes.toString(Charsets.UTF_8).take(30)}\""

    companion object {
        private val contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8).toString()
    }
}

