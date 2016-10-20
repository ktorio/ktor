package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.*

val Application.transform: ApplicationTransform<PipelineContext<ResponsePipelineState>>
    get() = feature(TransformationSupport)

val ApplicationCall.transform: ApplicationTransform<PipelineContext<ResponsePipelineState>>
    get() = attributes.computeIfAbsent(ApplicationCallTransform) { ApplicationTransform(application.transform.table) }


private val ApplicationCallTransform = AttributeKey<ApplicationTransform<PipelineContext<ResponsePipelineState>>>("ktor.transform")

fun PipelineContext<ResponsePipelineState>.proceed(message: Any): Nothing {
    if (subject.message !== message) {
        subject.message = message
        subject.attributes[TransformationState.Key].markLastHandlerVisited()
        repeat()
    }

    proceed()
}

fun <C : Any> TransformTable<C>.transform(ctx: C, obj: Any) = transformImpl(ctx, obj)

tailrec
private fun <C : Any, T : Any> TransformTable<C>.transformImpl(ctx: C, obj: T, handlers: List<TransformTable.Handler<C, T>> = handlers(obj.javaClass), visited: TransformTable.HandlersSet<C> = newHandlersSet()): Any {
    for (i in 0 .. handlers.size - 1) {
        val handler = handlers[i]

        if (handler !in visited && handler.predicate(ctx, obj)) {
            val result = handler.handler(ctx, obj)

            if (result === obj) {
                continue
            }

            visited.add(handler)
            if (result.javaClass === obj.javaClass) {
                @Suppress("UNCHECKED_CAST")
                return transformImpl(ctx, result as T, handlers, visited)
            } else {
                return transformImpl(ctx, result, handlers(result.javaClass), visited)
            }
        }
    }

    return obj
}

internal class TransformationState {
    val visited: MutableSet<TransformTable.Handler<PipelineContext<ResponsePipelineState>, *>> = hashSetOf()
    var completed: Boolean = false
    var lastHandler: TransformTable.Handler<PipelineContext<ResponsePipelineState>, *>? = null

    fun markLastHandlerVisited() {
        val handler = lastHandler
        lastHandler = null

        if (handler != null) {
            visited.add(handler)
        }
    }

    companion object {
        val Key = AttributeKey<TransformationState>("TransformationState.key")
    }
}
