package io.ktor.features

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import kotlin.properties.*

class PartialContentSupport(val maxRangeCount: Int) {
    class Configuration {
        var maxRangeCount: Int by Delegates.vetoable(10) { _, _, new ->
            new <= 0 || throw IllegalArgumentException("Bad maxRangeCount value $new")
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, PartialContentSupport> {
        val PartialContentPhase = PipelinePhase("PartialContent")

        override val key: AttributeKey<PartialContentSupport> = AttributeKey("Partial Content")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): PartialContentSupport {
            val feature = PartialContentSupport(Configuration().apply(configure).maxRangeCount)
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
                if (message is OutgoingContent.ReadChannelContent && message !is RangeChannelProvider) {
                    proceedWith(RangeChannelProvider.ByPass(message))
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
            if (message is OutgoingContent.ReadChannelContent && message !is RangeChannelProvider) {
                val length = message.contentLength() ?: return@response
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
            proceedWith(RangeChannelProvider.ByPass(content))
        }
    }

    private fun checkIfRangeHeader(obj: OutgoingContent.ReadChannelContent, call: ApplicationCall): Boolean {
        val versions = obj.lastModifiedAndEtagVersions()
        val ifRange = call.request.header(HttpHeaders.IfRange)

        return ifRange == null || versions.all { version ->
            when (version) {
                is EntityTagVersion -> version.etag in ifRange.parseMatchTag()
                is LastModifiedVersion -> version.lastModified <= ifRange.fromHttpDateString().toLocalDateTime()
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
        proceedWith(RangeChannelProvider.Single(call.isGet(), content, range, length))
    }

    private suspend fun PipelineContext<Any, ApplicationCall>.processMultiRange(content: OutgoingContent.ReadChannelContent, ranges: List<LongRange>, length: Long) {
        val boundary = "ktor-boundary-" + nextNonce()

        call.attributes.put(Compression.SuppressionAttribute, true) // multirange with compression is not supported yet

        val contentType = content.contentType() ?: ContentType.Application.OctetStream
        proceedWith(RangeChannelProvider.Multiple(call.isGet(), content, ranges, length, boundary, contentType))
    }

    private sealed class RangeChannelProvider : OutgoingContent.ReadChannelContent() {
        class ByPass(val content: ReadChannelContent) : RangeChannelProvider() {
            override val status: HttpStatusCode?
                get() = content.status

            override fun readFrom() = content.readFrom()

            override val headers by lazy {
                ValuesMap.build(true) {
                    appendAll(content.headers)
                    acceptRanges()
                }
            }
        }

        class Single(val get: Boolean, val content: OutgoingContent.ReadChannelContent, val range: LongRange, val fullLength: Long) : RangeChannelProvider() {
            override val status: HttpStatusCode? get() = if (get) HttpStatusCode.PartialContent else null

            override fun readFrom(): ByteReadChannel = content.readFrom(range)

            override val headers by lazy {
                ValuesMap.build(true) {
                    appendFiltered(content.headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                    acceptRanges()
                    contentRange(range, fullLength)
                }
            }
        }

        class Multiple(val get: Boolean, val content: OutgoingContent.ReadChannelContent, val ranges: List<LongRange>, val length: Long, val boundary: String, val contentType: ContentType) : RangeChannelProvider() {
            override val status: HttpStatusCode? get() = if (get) HttpStatusCode.PartialContent else null

            override fun readFrom() = writeMultipleRanges(
                    { range -> content.readFrom(range) }, ranges, length, boundary, contentType.toString()
            )

            override val headers: ValuesMap
                get() = ValuesMap.build(true) {
                    appendFiltered(content.headers) { name, _ ->
                        !name.equals(HttpHeaders.ContentType, true) && !name.equals(HttpHeaders.ContentLength, true)
                    }
                    acceptRanges()

                    contentType(ContentType.MultiPart.ByteRanges.withParameter("boundary", boundary))
                }
        }

        protected fun ValuesMapBuilder.acceptRanges() {
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
