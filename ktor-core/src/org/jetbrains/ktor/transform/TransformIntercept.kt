package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

object TransformationSupport : ApplicationFeature<TransformTable<PipelineContext<ResponsePipelineState>>> {
    override val name = "TransformationSupport"
    override val key = AttributeKey<TransformTable<PipelineContext<ResponsePipelineState>>>(name)

    override fun install(application: Application, configure: TransformTable<PipelineContext<ResponsePipelineState>>.() -> Unit): TransformTable<PipelineContext<ResponsePipelineState>> {
        val table = TransformTable<PipelineContext<ResponsePipelineState>>()

        configure(table)

        application.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.respond.intercept(RespondPipeline.Transform) { state ->
                transform()
            }
        }

        return table
    }

}

fun TransformTable<PipelineContext<ResponsePipelineState>>.registerDefaultHandlers() {
    register<String> { value ->
        val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
        val contentType = responseContentType ?: ContentType.Text.Plain.withParameter("charset", "UTF-8")
        TextContentResponse(null, contentType, value)
    }

    register<TextContent> { value -> TextContentResponse(null, value.contentType, value.text) }

    register<HttpStatusContent> { value ->
        TextContentResponse(value.code,
                ContentType.Text.Html.withParameter("charset", "UTF-8"),
                "<H1>${value.code}</H1>${value.message.escapeHTML()}")
    }

    register<HttpStatusCode> { value ->
        object : FinalContent.NoContent() {
            override val status: HttpStatusCode
                get() = value

            override val headers: ValuesMap
                get() = ValuesMap.Empty
        }
    }
}
