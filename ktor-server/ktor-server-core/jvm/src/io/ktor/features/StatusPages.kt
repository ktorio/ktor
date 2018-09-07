package io.ktor.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.*

/**
 * Status pages feature that handles exceptions and status codes. Useful to configure default error pages.
 */
class StatusPages(config: Configuration) {
    private val exceptions = HashMap(config.exceptions)
    private val statuses = HashMap(config.statuses)

    /**
     * Status pages feature config
     */
    class Configuration {
        /**
         * Exception handlers map by exception class
         */
        val exceptions = mutableMapOf<Class<*>, suspend PipelineContext<*, ApplicationCall>.(Throwable) -> Unit>()

        /**
         * Status handlers by status code
         */
        val statuses =
            mutableMapOf<HttpStatusCode, suspend PipelineContext<*, ApplicationCall>.(HttpStatusCode) -> Unit>()

        /**
         * Register exception [handler] for exception type [T] and it's children
         */
        inline fun <reified T : Throwable> exception(
            noinline handler: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
        ) =
            exception(T::class.java, handler)

        /**
         * Register exception [handler] for exception class [klass] and it's children
         */
        fun <T : Throwable> exception(
            klass: Class<T>,
            handler: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit
        ) {
            @Suppress("UNCHECKED_CAST")
            exceptions.put(klass, handler as suspend PipelineContext<*, ApplicationCall>.(Throwable) -> Unit)
        }

        /**
         * Register status [handler] for [status] code
         */
        fun status(
            vararg status: HttpStatusCode,
            handler: suspend PipelineContext<*, ApplicationCall>.(HttpStatusCode) -> Unit
        ) {
            status.forEach {
                statuses.put(it, handler)
            }
        }
    }

    private suspend fun interceptResponse(context: PipelineContext<*, ApplicationCall>, message: Any) {
        val call = context.call
        if (call.attributes.contains(key)) return

        val status = when (message) {
            is OutgoingContent -> message.status
            is HttpStatusCode -> message
            else -> null
        }
        if (status != null) {
            val handler = statuses[status]
            if (handler != null) {
                call.attributes.put(key, this@StatusPages)
                context.handler(status)
                context.finish() // TODO: Should we always finish? Handler could skip responding…
            }
        }
    }

    private suspend fun interceptCall(context: PipelineContext<Unit, ApplicationCall>) {
        try {
            coroutineScope {
                context.proceed()
            }
        } catch (exception: Throwable) {
            if (context.call.response.status() == null) {
                val handler = findHandlerByType(exception.javaClass)
                if (handler != null) {
                    context.handler(exception)
                } else
                    throw exception
            }
        }
    }

    private fun findHandlerByType(clazz: Class<*>): (suspend PipelineContext<Unit, ApplicationCall>.(Throwable) -> Unit)? {
        exceptions[clazz]?.let { return it }
        clazz.superclass?.let {
            findHandlerByType(it)?.let { return it }
        }
        clazz.interfaces.forEach {
            findHandlerByType(it)?.let { return it }
        }
        return null
    }

    /**
     * Feature installation object
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, StatusPages> {
        override val key = AttributeKey<StatusPages>("Status Pages")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): StatusPages {
            val configuration = Configuration().apply(configure)
            val feature = StatusPages(configuration)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
                feature.interceptResponse(this, message)
            }
            pipeline.intercept(ApplicationCallPipeline.Monitoring) { feature.interceptCall(this) }
            return feature
        }
    }
}

/**
 * Register a status page file(s) using [filePattern] for multiple status [code] list
 * @param code vararg list of status codes handled by this configuration
 * @param filePattern path to status file with optional `#` character(s) that will be replaced with numeric status code
 */
fun StatusPages.Configuration.statusFile(vararg code: HttpStatusCode, filePattern: String) {
    status(*code) { status ->
        val path = filePattern.replace("#", status.value.toString())
        val message = call.resolveResource(path)
        if (message == null) {
            call.respond(HttpStatusCode.InternalServerError)
        } else {
            call.response.status(status)
            call.respond(message)
        }
        finish()
    }
}
