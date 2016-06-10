package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.nio.charset.*

object TransformationSupport : ApplicationFeature<TransformTable<PipelineContext<ResponsePipelineState>>> {
    override val name = "TransformationSupport"
    override val key = AttributeKey<TransformTable<PipelineContext<ResponsePipelineState>>>(name)

    override fun install(application: Application, configure: TransformTable<PipelineContext<ResponsePipelineState>>.() -> Unit): TransformTable<PipelineContext<ResponsePipelineState>> {
        val table = TransformTable<PipelineContext<ResponsePipelineState>>()

        configure(table)

        application.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.respond.intercept(RespondPipeline.Transform) { state ->
                state.obj = call.transform.transform(this, state.obj)
            }
        }

        return table
    }

    fun registerDefaultHandlers(transform: TransformTable<PipelineContext<ResponsePipelineState>>) {
        transform.register<String> { value ->
            val encoding = call.response.headers[HttpHeaders.ContentType]?.let {
                ContentType.parse(it).parameter("charset")
            } ?: "UTF-8"

            TextContentResponse(null, null, encoding, value)
        }

        transform.register<TextContent> { value ->
            TextContentResponse(null, value.contentType,
                    value.contentType.parameter("charset") ?: "UTF-8",
                    value.text)
        }

        transform.register<HttpStatusContent> { value ->
            TextContentResponse(value.code,
                    ContentType.Text.Html.withParameter("charset", "UTF-8"), "UTF-8",
                    "<H1>${value.code}</H1>${value.message.escapeHTML()}")
        }

        transform.register<HttpStatusCode> { value ->
            object : FinalContent.NoContent() {
                override val status: HttpStatusCode
                    get() = value

                override val headers: ValuesMap
                    get() = ValuesMap.Empty
            }
        }
    }

    private class TextContentResponse(override val status: HttpStatusCode?, contentType: ContentType?, encoding: String, text: String) : FinalContent.ChannelContent() {
        private val bytes by lazy { text.toByteArray(Charset.forName(encoding)) }

        override val headers by lazy {
            ValuesMap.build(true) {
                if (contentType != null) {
                    contentType(contentType)
                }
                contentLength(bytes.size.toLong())
            }
        }

        override fun channel() = ByteArrayAsyncReadChannel(bytes)
    }

}
