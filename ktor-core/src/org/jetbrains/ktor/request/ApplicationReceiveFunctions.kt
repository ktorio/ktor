package org.jetbrains.ktor.request

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.pipeline.*
import kotlin.reflect.*

class ApplicationReceiveRequest(val type: KClass<*>, val value: Any)
open class ApplicationReceivePipeline : Pipeline<ApplicationReceiveRequest>(Before, Transform, After) {
    companion object Phases {
        val Before = PipelinePhase("Before")

        val Transform = PipelinePhase("Transform")

        val After = PipelinePhase("After")
    }
}

inline suspend fun <reified T : Any> ApplicationCall.tryReceive(): T? = tryReceive(T::class)
inline suspend fun <reified T : Any> ApplicationCall.receive(): T {
    val type = T::class
    return tryReceive(type) ?: throw Exception("Cannot transform this request's content into $type")
}

/**
 * Receive content for this request
 */
suspend fun <T : Any> ApplicationCall.tryReceive(type: KClass<T>): T? {
    val transformed = receivePipeline.execute(this, ApplicationReceiveRequest(type, request.receiveContent())).value
    if (transformed is IncomingContent)
        return null

    @Suppress("UNCHECKED_CAST")
    return transformed as? T
}


