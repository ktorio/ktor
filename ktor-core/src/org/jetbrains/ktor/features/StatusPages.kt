package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.util.*

class StatusPages(config: Configuration) {
    val exceptions = HashMap(config.exceptions)
    val statuses = HashMap(config.statuses)

    class Configuration {
        val exceptions = mutableMapOf<Class<*>, suspend PipelineContext<Unit>.(Throwable) -> Unit>()
        val statuses = mutableMapOf<HttpStatusCode, suspend PipelineContext<Unit>.(HttpStatusCode) -> Unit>()

        inline fun <reified T : Throwable> exception(noinline handler: suspend PipelineContext<Unit>.(T) -> Unit) =
                exception(T::class.java, handler)

        fun <T : Throwable> exception(klass: Class<T>, handler: suspend PipelineContext<Unit>.(T) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            exceptions.put(klass, handler as suspend PipelineContext<Unit>.(Throwable) -> Unit)
        }

        fun status(vararg status: HttpStatusCode, handler: suspend PipelineContext<Unit>.(HttpStatusCode) -> Unit) {
            status.forEach {
                statuses.put(it, handler)
            }
        }
    }

    suspend private fun intercept(context: PipelineContext<Unit>) {
        var statusHandled = false
        context.call.sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
            if (!statusHandled) {
                val status = when (message) {
                    is FinalContent -> message.status
                    is HttpStatusCode -> message
                    else -> null
                }
                val handler = statuses[status]
                if (handler != null) {
                    statusHandled = true
                    context.handler(status!!)
                    context.finish() // TODO: Should we always finish? Handler could skip responding…
                }
            }
        }

        try {
            context.proceed()
        } catch(exception: Throwable) {
            if (context.call.response.status() == null) {
                val handler = findHandlerByType(exception.javaClass)
                if (handler != null) {
                    context.handler(exception)
                } else
                    throw exception
            }
        }
    }

    private fun findHandlerByType(clazz: Class<*>): (suspend PipelineContext<Unit>.(Throwable) -> Unit)? {
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
            call.respond(message)
        }
        finish()
    }
}
