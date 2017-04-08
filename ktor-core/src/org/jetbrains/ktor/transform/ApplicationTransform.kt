package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

class ApplicationTransform<TContext : ApplicationCall>(private val parent: TransformTable<TContext>? = null) {
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

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, ApplicationTransform<ApplicationCall>, ApplicationTransform<ApplicationCall>> {
        override val key = AttributeKey<ApplicationTransform<ApplicationCall>>("Transformation Support")
        private val TransformApplicationPhase = PipelinePhase("Transform")
        internal val ApplicationCallTransform = AttributeKey<ApplicationTransform<ApplicationCall>>("ktor.transform")

        override fun install(pipeline: ApplicationCallPipeline, configure: ApplicationTransform<ApplicationCall>.() -> Unit): ApplicationTransform<ApplicationCall> {
            val table = ApplicationTransform<ApplicationCall>()

            configure(table)

            pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, TransformApplicationPhase)
            pipeline.intercept(TransformApplicationPhase) { call ->
                call.response.pipeline.intercept(ApplicationResponsePipeline.Transform) {
                    val transformator = call.attributes.getOrNull(ApplicationCallTransform) ?: call.application.transform
                    subject = transformator.table.transform(call, subject)
                }
            }

            return table
        }
    }
}

val Application.transform: ApplicationTransform<ApplicationCall>
    get() = feature(ApplicationTransform)

val ApplicationCall.transform: ApplicationTransform<ApplicationCall>
    get() = attributes.computeIfAbsent(ApplicationTransform.ApplicationCallTransform) { ApplicationTransform(application.transform.table) }

