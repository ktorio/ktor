package io.ktor.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import kotlin.properties.*

@Deprecated("Please use PartialContent instead", replaceWith = ReplaceWith("PartialContent"))
typealias PartialContentSupport = PartialContent

/**
 * Feature to support requests to specific content ranges.
 *
 * It is essential for streaming video and restarting downloads.
 *
 */
class PartialContent(private val maxRangeCount: Int) {

    /**
     * Configuration for [PartialContent].
     */
    class Configuration {
        /**
         * Maximum number of ranges that will be accepted from HTTP request.
         *
         * If HTTP request specifies more ranges, they will all be merged into a single range.
         */
        var maxRangeCount: Int by Delegates.vetoable(10) { _, _, new ->
            new > 0 || throw IllegalArgumentException("Bad maxRangeCount value $new")
        }
    }

    /**
     * `ApplicationFeature` implementation for [PartialContent]
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, PartialContent> {
        private val PartialContentPhase = PipelinePhase("PartialContent")

        override val key: AttributeKey<PartialContent> = AttributeKey("Partial Content")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): PartialContent {
            val feature = PartialContent(Configuration().apply(configure).maxRangeCount)
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(this) }
            return feature
        }
    }

    private suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val call = context.call
        val rangeSpecifier = call.request.ranges()
        if (rangeSpecifier == null) {
            call.response.pipeline.registerPhase()
            call.response.pipeline.intercept(PartialContentPhase) { message ->
                if (message is OutgoingContent.ReadChannelContent && message !is PartialOutgoingContent) {
                    proceedWith(PartialOutgoingContent.Bypass(message))
                }
            }
            return
        }

        if (!call.isGetOrHead()) {
            val message = HttpStatusCode.MethodNotAllowed.description("Method ${call.request.local.method.value} is not allowed with range request")
            call.respond(message)
            context.finish()
            return
        }

        call.response.pipeline.registerPhase()
        call.attributes.put(Compression.SuppressionAttribute, true)
        call.response.pipeline.intercept(PartialContentPhase) response@ { message ->
            if (message is OutgoingContent.ReadChannelContent && message !is PartialOutgoingContent) {
                val length = message.contentLength ?: return@response
                tryProcessRange(message, call, rangeSpecifier, length)
            }
        }
    }

    private fun ApplicationSendPipeline.registerPhase() {
        insertPhaseAfter(ApplicationSendPipeline.ContentEncoding, PartialContentPhase)
    }

    private suspend fun PipelineContext<Any, ApplicationCall>.tryProcessRange(
            content: OutgoingContent.ReadChannelContent,
            call: ApplicationCall,
            rangesSpecifier: RangesSpecifier,
            length: Long
    ) {
        if (checkIfRangeHeader(content, call)) {
            processRange(content, rangesSpecifier, length)
        } else {
            proceedWith(PartialOutgoingContent.Bypass(content))
        }
    }

    private suspend fun checkIfRangeHeader(content: OutgoingContent.ReadChannelContent, call: ApplicationCall): Boolean {
        val conditionalHeadersFeature = call.application.featureOrNull(ConditionalHeaders)
        val versions = conditionalHeadersFeature?.versionsFor(content) ?: content.defaultVersions
        val ifRange = call.request.header(HttpHeaders.IfRange)

        return ifRange == null || versions.all { version ->
            when (version) {
                is EntityTagVersion -> version.etag in ifRange.parseMatchTag()
                is LastModifiedVersion -> version.lastModified <= ifRange.fromHttpDateString()
                else -> true
            }
        }
    }

    private suspend fun PipelineContext<Any, ApplicationCall>.processRange(
            content: OutgoingContent.ReadChannelContent,
            rangesSpecifier: RangesSpecifier,
            length: Long
    ) {
        require(length >= 0L)
        val merged = rangesSpecifier.merge(length, maxRangeCount)
        if (merged.isEmpty()) {
            call.response.contentRange(range = null, fullLength = length) // https://tools.ietf.org/html/rfc7233#section-4.4
            val statusCode = HttpStatusCode.RequestedRangeNotSatisfiable.description("Couldn't satisfy range request $rangesSpecifier: it should comply with the restriction [0; $length)")
            proceedWith(HttpStatusCodeContent(statusCode))
            return
        }

        when {
            merged.size != 1 && !merged.isAscending() -> {
                // merge into single range for non-seekable channel
                processSingleRange(content, rangesSpecifier.mergeToSingle(length)!!, length)
            }
            merged.size == 1 -> processSingleRange(content, merged.single(), length)
            else -> processMultiRange(content, merged, length)
        }
    }

    private suspend fun PipelineContext<Any, ApplicationCall>.processSingleRange(content: OutgoingContent.ReadChannelContent, range: LongRange, length: Long) {
        proceedWith(PartialOutgoingContent.Single(call.isGet(), content, range, length))
    }

    private suspend fun PipelineContext<Any, ApplicationCall>.processMultiRange(content: OutgoingContent.ReadChannelContent, ranges: List<LongRange>, length: Long) {
        val boundary = "ktor-boundary-" + nextNonce()

        call.attributes.put(Compression.SuppressionAttribute, true) // multirange with compression is not supported yet

        proceedWith(PartialOutgoingContent.Multiple(call.isGet(), content, ranges, length, boundary))
    }

    private sealed class PartialOutgoingContent(val original: ReadChannelContent) : OutgoingContent.ReadChannelContent() {
        override val status: HttpStatusCode? get() = original.status
        override val contentType: ContentType? get() = original.contentType
        override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
        override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

        class Bypass(original: ReadChannelContent) : PartialOutgoingContent(original) {
            override fun readFrom() = original.readFrom()

            override val headers by lazy(LazyThreadSafetyMode.NONE) {
                Headers.build {
                    appendAll(original.headers)
                    acceptRanges()
                }
            }
        }

        class Single(val get: Boolean, original: OutgoingContent.ReadChannelContent, val range: LongRange, val fullLength: Long) : PartialOutgoingContent(original) {
            override val status: HttpStatusCode?
                get() = if (get) HttpStatusCode.PartialContent else original.status
            override val contentLength: Long? get() = null

            override fun readFrom(): ByteReadChannel = original.readFrom(range)

            override val headers by lazy(LazyThreadSafetyMode.NONE) {
                Headers.build {
                    appendFiltered(original.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                    acceptRanges()
                    contentRange(range, fullLength)
                }
            }
        }

        class Multiple(val get: Boolean, original: OutgoingContent.ReadChannelContent, val ranges: List<LongRange>, val length: Long, val boundary: String) : PartialOutgoingContent(original) {
            override val status: HttpStatusCode?
                get() = if (get) HttpStatusCode.PartialContent else original.status
            override val contentLength: Long? get() = null
            override val contentType: ContentType? get() = ContentType.MultiPart.ByteRanges.withParameter("boundary", boundary)

            override fun readFrom() = writeMultipleRanges(
                    { range -> original.readFrom(range) }, ranges, length, boundary, contentType.toString()
            )

            override val headers by lazy(LazyThreadSafetyMode.NONE) {
                Headers.build {
                    appendFiltered(original.headers) { name, _ ->
                        !name.equals(HttpHeaders.ContentType, true) && !name.equals(HttpHeaders.ContentLength, true)
                    }
                    acceptRanges()
                }
            }
        }

        protected fun HeadersBuilder.acceptRanges() {
            if (!contains(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)) {
                append(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)
            }
        }
    }

    private fun ApplicationCall.isGet() = request.local.method == HttpMethod.Get
    private fun ApplicationCall.isGetOrHead() = isGet() || request.local.method == HttpMethod.Head
    private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()
}

private fun List<LongRange>.isAscending(): Boolean = fold(true to 0L) { acc, e -> (acc.first && acc.second <= e.start) to e.start }.first
