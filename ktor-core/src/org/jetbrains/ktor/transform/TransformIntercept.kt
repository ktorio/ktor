package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*

object TransformationSupport : ApplicationFeature<ApplicationCallPipeline, ApplicationTransform<PipelineContext<ResponsePipelineState>>, ApplicationTransform<PipelineContext<ResponsePipelineState>>> {
    override val key = AttributeKey<ApplicationTransform<PipelineContext<ResponsePipelineState>>>("Transformation Support")
    private val TransformApplicationPhase = PipelinePhase("Transform")

    override fun install(pipeline: ApplicationCallPipeline, configure: ApplicationTransform<PipelineContext<ResponsePipelineState>>.() -> Unit): ApplicationTransform<PipelineContext<ResponsePipelineState>> {
        val table = ApplicationTransform<PipelineContext<ResponsePipelineState>>()

        configure(table)

        pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, TransformApplicationPhase)
        pipeline.intercept(TransformApplicationPhase) { call ->
            call.response.pipeline.intercept(RespondPipeline.Transform) { state ->
                transform()
            }
        }

        return table
    }

    private fun PipelineContext<ResponsePipelineState>.transform() {
        val machine = PipelineMachine()
        val phase = PipelinePhase("phase")
        val pipeline = Pipeline<ResponsePipelineState>(phase)
        val state = TransformationState()

        subject.attributes.put(TransformationState.Key, state)
        pipeline.intercept(phase) {
            onSuccess {
                this@transform.continuePipeline()
            }
            onFail { cause ->
                this@transform.runBlock { fail(cause) }
            }

            transformStage(machine, state)
        }

        machine.execute(subject, pipeline)
    }

    tailrec
    private fun PipelineContext<ResponsePipelineState>.transformStage(machine: PipelineMachine, state: TransformationState) {
        if (state.completed) {
            return
        }

        machine.appendDelayed(subject, listOf({ p ->
            @Suppress("NON_TAIL_RECURSIVE_CALL")
            transformStage(machine, state)
        }))

        val message = subject.message
        val visited = state.visited
        val handlers = subject.call.transform.handlers(message.javaClass).filter { it !in visited }

        if (handlers.isNotEmpty()) {
            for (handler in handlers) {
                if (handler.predicate(this, message)) {
                    state.lastHandler = handler
                    val nextResult = handler.handler(this, message)
                    state.lastHandler = null

                    if (nextResult !== message) {
                        subject.message = nextResult
                        visited.add(handler)
                        return transformStage(machine, state)
                    }
                }
            }
        }

        state.completed = true
    }

}

fun ApplicationTransform<PipelineContext<ResponsePipelineState>>.registerDefaultHandlers() {
    register<String> { value ->
        val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
        val contentType = responseContentType ?: ContentType.Text.Plain.withCharset(Charsets.UTF_8)
        TextContentResponse(null, contentType, value)
    }

    register<TextContent> { value -> TextContentResponse(null, value.contentType, value.text) }

    register<HttpStatusContent> { value ->
        TextContentResponse(value.code,
                ContentType.Text.Html.withCharset(Charsets.UTF_8),
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

    register<URIFileContent> { value ->
        if (value.uri.scheme == "file") {
            LocalFileContent(File(value.uri))
        } else value
    }
}
