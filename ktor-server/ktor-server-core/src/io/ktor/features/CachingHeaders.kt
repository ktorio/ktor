package io.ktor.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import java.util.*

class CachingHeaders(private val optionsProviders: List<(OutgoingContent) -> CachingOptions?>) {
    /**
     * Configuration for [CachingHeaders] feature
     */
    class Configuration {
        internal val optionsProviders = mutableListOf<(OutgoingContent) -> CachingOptions?>()

        init {
            optionsProviders.add { content -> content.caching }
        }

        /**
         * Registers a function that can provide caching options for a given [OutgoingContent]
         */
        fun options(provider: (OutgoingContent) -> CachingOptions?) {
            optionsProviders.add(provider)
        }
    }

    internal fun interceptor(context: PipelineContext<Any, ApplicationCall>, message: Any) {
        val call = context.call
        val options = if (message is OutgoingContent) optionsFor(message) else emptyList()

        if (options.isNotEmpty()) {
            val headers = Headers.build {
                options.forEach {
                    if (it.cacheControl != null)
                        append(HttpHeaders.CacheControl, it.cacheControl.toString())
                    if (it.expires != null)
                        append(HttpHeaders.Expires, it.expires.toHttpDateString())
                }
            }

            val responseHeaders = call.response.headers
            headers.forEach { name, values ->
                values.forEach { responseHeaders.append(name, it) }
            }
        }
    }

    /**
     * Retrieves caching options for a given content
     */
    fun optionsFor(content: OutgoingContent): List<CachingOptions> {
        return optionsProviders.mapNotNullTo(ArrayList(optionsProviders.size)) { it(content) }
    }

    /**
     * `ApplicationFeature` implementation for [ConditionalHeaders]
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, CachingHeaders.Configuration, CachingHeaders> {
        override val key = AttributeKey<CachingHeaders>("Conditional Headers")
        override fun install(pipeline: ApplicationCallPipeline, configure: CachingHeaders.Configuration.() -> Unit): CachingHeaders {
            val configuration = CachingHeaders.Configuration().apply(configure)
            val feature = CachingHeaders(configuration.optionsProviders)

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { message -> feature.interceptor(this, message) }

            return feature
        }
    }
}