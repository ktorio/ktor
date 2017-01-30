package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

object TransformationSupport : ApplicationFeature<ApplicationCallPipeline, ApplicationTransform<PipelineContext<ResponseMessage>>, ApplicationTransform<PipelineContext<ResponseMessage>>> {
    override val key = AttributeKey<ApplicationTransform<PipelineContext<ResponseMessage>>>("Transformation Support")
    private val TransformApplicationPhase = PipelinePhase("Transform")

    override fun install(pipeline: ApplicationCallPipeline, configure: ApplicationTransform<PipelineContext<ResponseMessage>>.() -> Unit): ApplicationTransform<PipelineContext<ResponseMessage>> {
        val table = ApplicationTransform<PipelineContext<ResponseMessage>>()

        configure(table)

        pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, TransformApplicationPhase)
        pipeline.intercept(TransformApplicationPhase) { call ->
            call.response.pipeline.intercept(RespondPipeline.Transform) {
                val message = subject.message
                val newMessage = call.transform.table.transform(this, message)
                subject.message = newMessage
            }
        }

        return table
    }
}

val Application.transform: ApplicationTransform<PipelineContext<ResponseMessage>>
    get() = feature(TransformationSupport)

val ApplicationCall.transform: ApplicationTransform<PipelineContext<ResponseMessage>>
    get() = attributes.computeIfAbsent(ApplicationCallTransform) { ApplicationTransform(application.transform.table) }

private val ApplicationCallTransform = AttributeKey<ApplicationTransform<PipelineContext<ResponseMessage>>>("ktor.transform")

suspend fun <C : Any> TransformTable<C>.transform(ctx: C, obj: Any): Any {
    val visited: TransformTable.HandlersSet<C> = newHandlersSet()
    var value: Any = obj
    var handlers = handlers(obj.javaClass)

    nextValue@ while (true) {
        for (i in 0..handlers.lastIndex) {
            val handler = handlers[i]

            if (handler in visited || !handler.predicate(ctx, value))
                continue

            val result = handler.handler(ctx, value)
            if (result === value)
                continue

            visited.add(handler)
            if (result.javaClass !== value.javaClass) {
                handlers = handlers(result.javaClass)
            }
            value = result
            continue@nextValue
        }
        break
    }

    return value
}