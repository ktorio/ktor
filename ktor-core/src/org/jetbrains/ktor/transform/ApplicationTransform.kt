package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

class ApplicationTransform<TContext : Any>(private val parent: TransformTable<TContext>? = null) {
    var table: TransformTable<TContext> = parent ?: TransformTable()
        private set

    inline fun <reified T : Any> register(noinline handler: suspend TContext.(T) -> Any) {
        register({ true }, handler)
    }

    inline fun <reified T : Any> register(noinline predicate: TContext.(T) -> Boolean, noinline handler: suspend TContext.(T) -> Any) {
        register(T::class, predicate, handler)
    }

    fun <T : Any> register(type: KClass<T>, predicate: TContext.(T) -> Boolean, handler: suspend TContext.(T) -> Any) {
        register(type.javaObjectType, predicate, handler)
    }

    fun <T : Any> register(type: Class<T>, predicate: TContext.(T) -> Boolean, handler: suspend TContext.(T) -> Any) {
        if (table === parent) {
            table = TransformTable(parent)
        }

        table.register(type, predicate, handler)
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, ApplicationTransform<PipelineContext<ResponseMessage>>, ApplicationTransform<PipelineContext<ResponseMessage>>> {
        override val key = AttributeKey<ApplicationTransform<PipelineContext<ResponseMessage>>>("Transformation Support")
        private val TransformApplicationPhase = PipelinePhase("Transform")
        internal val ApplicationCallTransform = AttributeKey<ApplicationTransform<PipelineContext<ResponseMessage>>>("ktor.transform")

        override fun install(pipeline: ApplicationCallPipeline, configure: ApplicationTransform<PipelineContext<ResponseMessage>>.() -> Unit): ApplicationTransform<PipelineContext<ResponseMessage>> {
            val table = ApplicationTransform<PipelineContext<ResponseMessage>>()

            configure(table)

            pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, TransformApplicationPhase)
            pipeline.intercept(TransformApplicationPhase) { call ->
                call.response.pipeline.intercept(RespondPipeline.Transform) {
                    val message = subject.message
                    val newMessage = (call.attributes.getOrNull(ApplicationCallTransform) ?: call.application.transform)
                            .table.transform(this, message)
                    subject.message = newMessage
                }
            }

            return table
        }
    }
}

val Application.transform: ApplicationTransform<PipelineContext<ResponseMessage>>
    get() = feature(ApplicationTransform)

val ApplicationCall.transform: ApplicationTransform<PipelineContext<ResponseMessage>>
    get() = attributes.computeIfAbsent(ApplicationTransform.ApplicationCallTransform) { ApplicationTransform(application.transform.table) }

