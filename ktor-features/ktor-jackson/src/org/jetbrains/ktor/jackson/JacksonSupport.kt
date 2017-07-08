package org.jetbrains.ktor.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class JacksonSupport(val mapper: ObjectMapper) {
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, ObjectMapper, JacksonSupport> {
        override val key = AttributeKey<JacksonSupport>("jackson")

        override fun install(pipeline: ApplicationCallPipeline, configure: ObjectMapper.() -> Unit): JacksonSupport {
            val mapper = ObjectMapper().registerKotlinModule().apply(configure)
            val feature = JacksonSupport(mapper)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) {
                if (it !is FinalContent && call.request.acceptItems().any { ContentType.Application.Json.match(it.value) }) {
                    proceedWith(feature.renderJsonContent(it))
                }
            }
            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                if (call.request.contentType().match(ContentType.Application.Json)) {
                    val message = it.value as? IncomingContent ?: return@intercept
                    val json = message.readText()
                    val value = mapper.readValue(json, it.type.javaObjectType)
                    proceedWith(ApplicationReceiveRequest(it.type, value))
                }
            }
            return feature
        }
    }

    private fun renderJsonContent(model: Any): JsonContent {
        val json = mapper.writeValueAsString(model)
        return JsonContent(json, HttpStatusCode.OK)
    }
}

private class JsonContent(val text: String, override val status: HttpStatusCode? = null) : FinalContent.ByteArrayContent() {
    private val bytes by lazy { text.toByteArray(Charsets.UTF_8) }

    override val headers by lazy {
        ValuesMap.build(true) {
            contentType(contentType)
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
    override fun toString() = "JsonContent \"${text.take(30)}\""

    companion object {
        private val contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)
    }
}

