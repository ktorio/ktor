package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.*

inline fun x(body : suspend ()->Unit) {

}

class StatusPages(config: Configuration) {
    val exceptions = HashMap(config.exceptions)
    val statuses = HashMap(config.statuses)

    class Configuration {
        val exceptions = mutableMapOf<Class<*>, PipelineInterceptor<Throwable>>()
        val statuses = mutableMapOf<HttpStatusCode, PipelineInterceptor<HttpStatusCode>>()

        inline fun <reified T : Any> exception(handler: PipelineInterceptor<Throwable>) =
                exception(T::class.java, handler)

        fun exception(klass: Class<*>, handler: PipelineInterceptor<Throwable>) {
            exceptions.put(klass, handler)
        }

        fun status(vararg status: HttpStatusCode, handler: PipelineInterceptor<HttpStatusCode>) {
            status.forEach {
                statuses.put(it, handler)
            }
        }
    }

    suspend private fun intercept(context: PipelineContext<ApplicationCall>) {
        context.onFail {
            // if response is already specified, do nothing
            if (call.response.status() == null) {
                val failure = exception!!
                val handler = findHandlerByType(failure.javaClass)
                if (handler != null) {
                    context.handler(failure)
                }
            }
        }
        var statusHandled = false
        context.call.response.pipeline.intercept(RespondPipeline.After) {
            if (!statusHandled) {
                val obj = subject.message
                val status = when (obj) {
                    is FinalContent -> obj.status
                    is HttpStatusCode -> obj
                    else -> null
                }
                val handler = statuses[status]
                if (handler != null) {
                    statusHandled = true
                    context.handler(status!!)
                }
            }
        }
    }

    private fun findHandlerByType(clazz: Class<*>): (PipelineContext<ApplicationCall>.(Throwable) -> Unit)? {
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

suspend fun StatusPages.Configuration.statusFile(vararg status: HttpStatusCode, filePattern: String, contentType: ContentType = ContentType.Text.Html) {
    status(*status) { status ->
        val path = filePattern.replace("#", status.value.toString())
        val message = call.resolveClasspathWithPath("", path)
        if (message == null) {
            call.respond(HttpStatusCode.InternalServerError)
        } else {
            call.respond(message)
        }
    }
}

@Deprecated("Use StatusPages feature instead", level = DeprecationLevel.ERROR)
fun Pipeline<ApplicationCall>.statusPage(phase: PipelinePhase = ApplicationCallPipeline.Infrastructure, handler: PipelineContext<ApplicationCall>.(HttpStatusCode) -> Unit): Unit {
    TODO("Deprecated feature. Use StatusPages feature instead")
}

@Deprecated("Use StatusPages feature instead", level = DeprecationLevel.ERROR)
fun Pipeline<ApplicationCall>.errorPage(phase: PipelinePhase = ApplicationCallPipeline.Infrastructure, handler: PipelineContext<ApplicationCall>.(Throwable) -> Unit) {
    TODO("Deprecated. Use StatusPages feature instead")
}
