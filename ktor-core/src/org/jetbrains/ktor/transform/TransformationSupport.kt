package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

object TransformationSupport : ApplicationFeature<ApplicationCallPipeline, ApplicationTransform<PipelineContext<ResponsePipelineState>>, ApplicationTransform<PipelineContext<ResponsePipelineState>>> {
    override val key = AttributeKey<ApplicationTransform<PipelineContext<ResponsePipelineState>>>("Transformation Support")
    private val TransformApplicationPhase = PipelinePhase("Transform")

    override fun install(pipeline: ApplicationCallPipeline, configure: ApplicationTransform<PipelineContext<ResponsePipelineState>>.() -> Unit): ApplicationTransform<PipelineContext<ResponsePipelineState>> {
        val table = ApplicationTransform<PipelineContext<ResponsePipelineState>>()

        configure(table)

        pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, TransformApplicationPhase)
        pipeline.intercept(TransformApplicationPhase) { call ->
            val transformationState = TransformationState()
            call.response.pipeline.intercept(RespondPipeline.Transform) response@ { state ->
                subject.attributes.put(TransformationState.Key, transformationState)
                val message = subject.message
                val visited = transformationState.visited
                val handlers = call.transform.handlers(message.javaClass).filter { it !in visited }
                if (handlers.isEmpty()) {
                    return@response
                }

                for (handler in handlers) {
                    if (handler.predicate(this, message)) {
                        transformationState.lastHandler = handler
                        val nextResult = handler.handler(this, message)
                        transformationState.lastHandler = null

                        if (nextResult !== message) {
                            subject.message = nextResult
                            visited.add(handler)
                            repeat()
                            break
                        }
                    }
                }
            }
        }

        return table
    }
}
