package io.ktor.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import java.util.*

class StatusPages(config: Configuration) {
    private val exceptions = HashMap(config.exceptions)
    private val statuses = HashMap(config.statuses)

    class Configuration {
        val exceptions = mutableMapOf<Class<*>, suspend PipelineContext<Unit, ApplicationCall>.(Throwable) -> Unit>()
        val statuses = mutableMapOf<HttpStatusCode, suspend PipelineContext<Unit, ApplicationCall>.(HttpStatusCode) -> Unit>()

        inline fun <reified T : Throwable> exception(noinline handler: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit) =
                exception(T::class.java, handler)

        fun <T : Throwable> exception(klass: Class<T>, handler: suspend PipelineContext<Unit, ApplicationCall>.(T) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            exceptions.put(klass, handler as suspend PipelineContext<Unit, ApplicationCall>.(Throwable) -> Unit)
        }

        fun status(vararg status: HttpStatusCode, handler: suspend PipelineContext<Unit, ApplicationCall>.(HttpStatusCode) -> Unit) {
            status.forEach {
                statuses.put(it, handler)
            }
        }
    }

    private suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        var statusHandled = false
        context.call.response.pipeline.intercept(ApplicationSendPipeline.After) { message ->
            if (!statusHandled) {
                val status = when (message) {
                    is OutgoingContent -> message.status
                    is HttpStatusCode -> message
                    else -> null
                }
                if (status != null) {
                    val handler = statuses[status]
                    if (handler != null) {
                        statusHandled = true
                        context.handler(status)
                        finish() // TODO: Should we always finish? Handler could skip responding…
                    }
                }
            }
        }

        try {
            context.proceed()
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

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, StatusPages> {
        override val key = AttributeKey<StatusPages>("Status Pages")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): StatusPages {
            val configuration = Configuration().apply(configure)
            val feature = StatusPages(configuration)
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(this) }
            return feature
        }
    }
}

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
