package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import kotlin.properties.*

class PartialContentSupport(val maxRangeCount : Int) {
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
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(it) }
            return feature
        }
    }

    suspend fun intercept(call: ApplicationCall) {
        val rangeSpecifier = call.request.ranges()
        if (rangeSpecifier != null) {
            if (call.isGetOrHead()) {
                call.attributes.put(Compression.SuppressionAttribute, true)
                call.response.pipeline.registerPhase()
                call.response.pipeline.intercept(PartialContentPhase) {
                    val message = subject
                    if (message is FinalContent.ReadChannelContent && message !is RangeChannelProvider) {
                        message.contentLength()?.let { length -> tryProcessRange(message, call, rangeSpecifier, length) }
                    }
                }
            } else {
                call.respond(HttpStatusCode.MethodNotAllowed.description("Method ${call.request.local.method.value} is not allowed with range request"))
            }
        } else {
            call.response.pipeline.registerPhase()
            call.response.pipeline.intercept(PartialContentPhase) {
                val message = subject
                if (message is FinalContent.ReadChannelContent && message !is RangeChannelProvider) {
                    call.respond(RangeChannelProvider.ByPass(message))
                }
            }
        }
    }

    private fun ApplicationResponsePipeline.registerPhase() {
        phases.insertAfter(ApplicationResponsePipeline.ContentEncoding, PartialContentPhase)
    }

    suspend private fun PipelineContext<*>.tryProcessRange(obj: FinalContent.ReadChannelContent, call: ApplicationCall, rangesSpecifier: RangesSpecifier, length: Long): Unit {
        if (checkIfRangeHeader(obj, call)) {
            processRange(obj, call, rangesSpecifier, length)
        } else {
            call.respond(RangeChannelProvider.ByPass(obj))
        }
    }

    private fun checkIfRangeHeader(obj: FinalContent.ReadChannelContent, call: ApplicationCall): Boolean {
        val versions = obj.lastModifiedAndEtagVersions()
        val ifRange = call.request.header(HttpHeaders.IfRange)

        val unchanged = ifRange == null || versions.all { version ->
            when (version) {
                is EntityTagVersion -> version.etag in ifRange.parseMatchTag()
                is LastModifiedVersion -> version.lastModified <= ifRange.fromHttpDateString().toLocalDateTime()
                else -> true
            }
        }

        return unchanged
    }


    suspend private fun processRange(obj: FinalContent.ReadChannelContent, call: ApplicationCall, rangesSpecifier: RangesSpecifier, length: Long) {
        require(length >= 0L)

        val merged = rangesSpecifier.merge(length, maxRangeCount)
        if (merged.isEmpty()) {
            call.response.contentRange(range = null, fullLength = length) // https://tools.ietf.org/html/rfc7233#section-4.4
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable.description("Couldn't satisfy range request $rangesSpecifier: it should comply with the restriction [0; $length)"))
            return
        }

        val channel = obj.readFrom()

        when {
            merged.size != 1 && !merged.isAscending() && channel !is RandomAccessReadChannel -> {
                // merge into single range for non-seekable channel
                processSingleRange(obj, call, channel, rangesSpecifier.mergeToSingle(length)!!, length)
            }
            merged.size == 1 -> {
                processSingleRange(obj, call, channel, merged.single(), length)
            }
            else -> {
                processMultiRange(obj, call, channel, merged, length)
            }
        }
        channel.close()
    }

    suspend private fun processSingleRange(obj: FinalContent.ReadChannelContent, call: ApplicationCall, channel: ReadChannel, range: LongRange, length: Long) {
        call.respond(RangeChannelProvider.Single(call.isGet(), obj.headers, channel, range, length))
    }

    suspend private fun processMultiRange(obj: FinalContent.ReadChannelContent, call: ApplicationCall, channel: ReadChannel, ranges: List<LongRange>, length: Long) {
        val boundary = "ktor-boundary-" + nextNonce()

        call.attributes.put(Compression.SuppressionAttribute, true) // multirange with compression is not supported yet

        val contentType = obj.contentType() ?: ContentType.Application.OctetStream
        call.respond(RangeChannelProvider.Multiple(call.isGet(), obj.headers, channel, ranges, length, boundary, contentType))
    }

    private sealed class RangeChannelProvider : FinalContent.ReadChannelContent() {
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

        class Single(val get: Boolean, val delegateHeaders: ValuesMap, val source: ReadChannel, val range: LongRange, val fullLength: Long) : RangeChannelProvider() {
            override val status: HttpStatusCode? get() = if (get) HttpStatusCode.PartialContent else null
            override fun readFrom() = RangeReadChannel(source, range.start, range.length, closeSource = false)
            override val headers by lazy {
                ValuesMap.build(true) {
                    appendFiltered(delegateHeaders) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
                    acceptRanges()
                    contentRange(range, fullLength)
                }
            }
        }

        class Multiple(val get: Boolean, val delegateHeaders: ValuesMap, val source: ReadChannel, val ranges: List<LongRange>, val length: Long, val boundary: String, val contentType: ContentType) : RangeChannelProvider() {
            override val status: HttpStatusCode? get() = if (get) HttpStatusCode.PartialContent else null

            override fun readFrom() = MultipleRangesReadChannel.create(source, ranges, length, boundary, contentType.toString())

            override val headers: ValuesMap
                get() = ValuesMap.build(true) {
                    appendFiltered(delegateHeaders) { name, _ ->
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
