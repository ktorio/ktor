package io.ktor.request

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.pipeline.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import kotlin.reflect.*

class ApplicationReceiveRequest(val type: KClass<*>, val value: Any)
open class ApplicationReceivePipeline : Pipeline<ApplicationReceiveRequest, ApplicationCall>(Before, Transform, After) {
    companion object Phases {
        val Before = PipelinePhase("Before")

        val Transform = PipelinePhase("Transform")

        val After = PipelinePhase("After")
    }
}

@Deprecated("Use receiveOrNull instead", ReplaceWith("this.receiveOrNull<T>()"))
inline suspend fun <reified T : Any> ApplicationCall.tryReceive(): T? = receiveOrNull()

inline suspend fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = receiveOrNull(T::class)
inline suspend fun <reified T : Any> ApplicationCall.receive(): T {
    val type = T::class
    return receiveOrNull(type) ?: throw ContentTransformationException("Cannot transform this request's content to $type")
}

/**
 * Receive content for this request
 */
suspend fun <T : Any> ApplicationCall.receiveOrNull(type: KClass<T>): T? {
    val incomingContent = request.receiveContent()
    val receiveRequest = ApplicationReceiveRequest(type, incomingContent)
    val transformed = request.pipeline.execute(this, receiveRequest).value
    if (transformed is IncomingContent)
        return null

    @Suppress("UNCHECKED_CAST")
    return transformed as? T
}

@Suppress("NOTHING_TO_INLINE")
inline suspend fun ApplicationCall.receiveText(): String = receive()

@Suppress("NOTHING_TO_INLINE")
inline suspend fun ApplicationCall.receiveChannel(): ByteReadChannel = receive()

@Suppress("NOTHING_TO_INLINE")
inline suspend fun ApplicationCall.receiveStream(): InputStream = receive()

@Suppress("NOTHING_TO_INLINE")
inline suspend fun ApplicationCall.receiveMultipart(): MultiPartData = receive()

@Suppress("NOTHING_TO_INLINE")
inline suspend fun ApplicationCall.receiveParameters(): ValuesMap = receive()

class ContentTransformationException(message: String) : Exception(message)