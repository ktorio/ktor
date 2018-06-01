package io.ktor.request

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import kotlin.reflect.*

/**
 * Represents a subject for [ApplicationReceivePipeline]
 * @param type specifies the desired type for a receiving operation
 * @param value specifies current value being processed by the pipeline
 */
class ApplicationReceiveRequest(val type: KClass<*>, val value: Any)

/**
 * Pipeline for processing incoming content
 *
 * When executed, this pipeline starts with an instance of [ByteReadChannel] and should finish with the requested type.
 */
open class ApplicationReceivePipeline : Pipeline<ApplicationReceiveRequest, ApplicationCall>(Before, Transform, After) {
    companion object Phases {
        /**
         * Executes before any transformations are made
         */
        val Before = PipelinePhase("Before")

        /**
         * Executes transformations
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Executes after all transformations
         */
        val After = PipelinePhase("After")
    }
}

/**
 * Receives content for this request.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type.
 */
suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = receiveOrNull(T::class)

/**
 * Receives content for this request.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
suspend inline fun <reified T : Any> ApplicationCall.receive(): T = receive(T::class)

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
suspend fun <T : Any> ApplicationCall.receive(type: KClass<T>): T {
    val incomingContent = request.receiveChannel()
    val receiveRequest = ApplicationReceiveRequest(type, incomingContent)
    val transformed = request.pipeline.execute(this, receiveRequest).value

    if (!type.isInstance(transformed))
        throw CannotTransformToTypeException(type)

    @Suppress("UNCHECKED_CAST")
    return transformed as T
}

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type..
 */
suspend fun <T : Any> ApplicationCall.receiveOrNull(type: KClass<T>): T? {
    return try {
        receive(type)
    } catch (cause: ContentTransformationException) {
        application.log.debug("Conversion failed, null returned", cause)
        null
    }
}

/**
 * Receives incoming content for this call as [String].
 * @return text received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the [String].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveText(): String = receive()

/**
 * Receives channel content for this call.
 * @return instance of [ByteReadChannel] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [ByteReadChannel].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveChannel(): ByteReadChannel = receive()

/**
 * Receives stream content for this call.
 * @return instance of [InputStream] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [InputStream].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveStream(): InputStream = receive()

/**
 * Receives multipart data for this call.
 * @return instance of [MultiPartData].
 * @throws ContentTransformationException when content cannot be transformed to the [MultiPartData].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveMultipart(): MultiPartData = receive()

/**
 * Receives form parameters for this call.
 * @return instance of [Parameters].
 * @throws ContentTransformationException when content cannot be transformed to the [Parameters].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveParameters(): Parameters = receive()

/**
 * Thrown when content cannot be transformed to the desired type.
 */
abstract class ContentTransformationException(message: String) : Exception(message)

private class CannotTransformToTypeException(type: KClass<*>)
    : ContentTransformationException("Cannot transform this request's content to $type")
