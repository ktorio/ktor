package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import kotlin.properties.*

object PartialContentSupport : ApplicationFeature<PartialContentSupport.Configuration> {
    override val name = "Ranges support"
    override val key: AttributeKey<Configuration> = AttributeKey(name)

    class Configuration {
        var maxRangeCount: Int by Delegates.vetoable(10) { p, old, new ->
            new <= 0 || throw IllegalArgumentException("Bad maxRangeCount value $new")
        }
    }

    override fun install(application: Application, configure: Configuration.() -> Unit): Configuration {
        val config = Configuration()
        configure(config)

        application.intercept(ApplicationCallPipeline.Infrastructure) { requestNext ->
            val rangeSpecifier = call.request.ranges()
            if (rangeSpecifier != null) {
                if (call.isGetOrHead()) {
                    call.attributes.put(CompressionAttributes.preventCompression, true)
                    call.interceptRespond(RespondPipeline.Before) { obj ->
                        if (obj is FinalContent.ChannelContent && obj !is RangeChannelProvider) {
                            obj.contentLength()?.let { length -> tryProcessRange(obj, call, rangeSpecifier, length, config) }
                        }
                    }
                } else {
                    call.respond(HttpStatusCode.MethodNotAllowed.description("Method ${call.request.httpMethod.value} is not allowed with range request"))
                }
            } else {
                call.interceptRespond(RespondPipeline.Before) { obj ->
                    if (obj is FinalContent.ChannelContent && obj !is RangeChannelProvider) {
                        call.respond(RangeChannelProvider.ByPass(obj))
                    }
                }
            }
        }

        return config
    }

    private fun PipelineContext<*>.tryProcessRange(obj: FinalContent.ChannelContent, call: ApplicationCall, rangesSpecifier: RangesSpecifier, length: Long, config: Configuration): Unit {
        if (checkIfRangeHeader(obj, call)) {
            processRange(obj, call, rangesSpecifier, length, config)
        } else {
            call.respond(RangeChannelProvider.ByPass(obj))
        }
    }

    private fun checkIfRangeHeader(obj: FinalContent.ChannelContent, call: ApplicationCall): Boolean {
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


    private fun PipelineContext<*>.processRange(obj: FinalContent.ChannelContent, call: ApplicationCall, rangesSpecifier: RangesSpecifier, length: Long, config: Configuration): Nothing {
        require(length >= 0L)

        val merged = rangesSpecifier.merge(length, config.maxRangeCount)
        if (merged.isEmpty()) {
            call.response.contentRange(range = null, fullLength = length) // https://tools.ietf.org/html/rfc7233#section-4.4
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable.description("Couldn't satisfy range request $rangesSpecifier: it should comply with the restriction [0; $length)"))
        }

        val channel = obj.channel()
        onFail { channel.close() }
        onSuccess { channel.close() }

        if (merged.size != 1 && !merged.isAscending() && channel !is SeekableAsyncChannel) {
            // merge into single range for non-seekable channel
            processSingleRange(obj, call, channel, rangesSpecifier.mergeToSingle(length)!!, length)
        }

        if (merged.size == 1) {
            processSingleRange(obj, call, channel, merged.single(), length)
        }

        processMultiRange(obj, call, channel, merged, length)
    }

    private fun processSingleRange(obj: FinalContent.ChannelContent, call: ApplicationCall, channel: AsyncReadChannel, range: LongRange, length: Long): Nothing {
        call.respond(RangeChannelProvider.Single(call.isGet(), obj.headers, channel, range, length))
    }

    private fun processMultiRange(obj: FinalContent.ChannelContent, call: ApplicationCall, channel: AsyncReadChannel, ranges: List<LongRange>, length: Long): Nothing {
        val boundary = "ktor-boundary-" + nextNonce()

        call.attributes.put(CompressionAttributes.preventCompression, true) // multirange with compression is not supported yet

        val contentType = obj.contentType() ?: ContentType.Application.OctetStream
        call.respond(RangeChannelProvider.Multiple(call.isGet(), obj.headers, channel, ranges, length, boundary, contentType))
    }

    private sealed class RangeChannelProvider : FinalContent.ChannelContent() {
        class ByPass(val delegate: FinalContent.ChannelContent) : RangeChannelProvider() {
            override val status: HttpStatusCode?
                get() = delegate.status

            override fun channel() = delegate.channel()

            override val headers by lazy {
                ValuesMap.build(true) {
                    appendAll(delegate.headers)
                    acceptRanges()
                }
            }
        }

        class Single(val get: Boolean, val delegateHeaders: ValuesMap, val delegate: AsyncReadChannel, val range: LongRange, val fullLength: Long) : RangeChannelProvider() {
            override val status: HttpStatusCode? get() = if (get) HttpStatusCode.PartialContent else null

            override fun channel() = when (delegate) {
                is SeekableAsyncChannel -> AsyncSeekAndCut(delegate, range.start, range.length, preventClose = true)
                else -> AsyncSkipAndCut(delegate, range.start, range.length, preventClose = true)
            }

            override val headers by lazy {
                ValuesMap.build(true) {
                    appendAll(delegateHeaders.filter { name, value ->
                        !name.equals(HttpHeaders.ContentLength, true)
                    })

                    acceptRanges()
                    contentRange(range, fullLength)
                }
            }
        }

        class Multiple(val get: Boolean, val delegateHeaders: ValuesMap, val delegate: AsyncReadChannel, val ranges: List<LongRange>, val length: Long, val boundary: String, val contentType: ContentType) : RangeChannelProvider() {
            override val status: HttpStatusCode? get() = if (get) HttpStatusCode.PartialContent else null

            override fun channel() = ByteRangesChannel.forRegular(ranges, delegate, length, boundary, contentType.toString())

            override val headers: ValuesMap
                get() = ValuesMap.build(true) {
                    appendAll(delegateHeaders.filter { name, value ->
                        !name.equals(HttpHeaders.ContentType, true) &&
                                !name.equals(HttpHeaders.ContentLength, true)
                    })
                    acceptRanges()

                    contentType(ContentType.MultiPart.ByteRanges.withParameter("boundary", boundary))
                }
        }

        protected fun ValuesMapBuilder.acceptRanges() {
            append(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)
        }
    }

    private fun ApplicationCall.isGet() = request.httpMethod == HttpMethod.Get
    private fun ApplicationCall.isGetOrHead() = isGet() || request.httpMethod == HttpMethod.Head
    private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()
}
