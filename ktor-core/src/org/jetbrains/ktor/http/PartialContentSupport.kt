package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.time.*
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

        application.intercept(0) { requestNext ->
            val rangeSpecifier = call.request.ranges()
            if (rangeSpecifier != null) {
                if (call.isGetOrHead()) {
                    call.attributes.put(CompressionAttributes.preventCompression, true)
                    call.interceptRespond(0) { obj ->
                        if (obj is FinalContent.ChannelContent && obj !is RangeChannelProvider) {
                            @Suppress("UNCHECKED_CAST")
                            val newContext = this as PipelineContext<FinalContent.ChannelContent>

                            obj.contentLength()?.let { length -> newContext.tryProcessRange(call, rangeSpecifier, length, config) }
                        }
                    }
                } else {
                    call.respond(HttpStatusCode.MethodNotAllowed.description("Method ${call.request.httpMethod.value} is not allowed with range request"))
                }
            } else {
                call.interceptRespond(0) { obj ->
                    if (obj is FinalContent.ChannelContent && obj !is RangeChannelProvider) {
                        call.respond(RangeChannelProvider.ByPass(obj))
                    }
                }
            }
        }

        return config
    }

    private fun FinalContent.contentLength(): Long? {
        if (this is Resource) {
            return contentLength
        }

        return headers[HttpHeaders.ContentLength]?.let { it.toLong() }
    }

    private fun FinalContent.contentType(): ContentType? {
        if (this is Resource) {
            return contentType
        }

        return headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
    }

    private fun FinalContent.versions(): List<Version> {
        if (this is HasVersions) {
            return versions
        }

        val headers = headers
        return headers.getAll(HttpHeaders.LastModified).orEmpty().map { LastModifiedVersion(LocalDateTime.parse(it, httpDateFormat)) } +
                headers.getAll(HttpHeaders.ETag).orEmpty().map { EntityTagVersion(it) }
    }

    private fun PipelineContext<FinalContent.ChannelContent>.tryProcessRange(call: ApplicationCall, rangesSpecifier: RangesSpecifier, length: Long, config: Configuration): Unit {
        if (checkIfRangeHeader(call)) {
            processRange(call, rangesSpecifier, length, config)
        } else {
            call.respond(RangeChannelProvider.ByPass(subject))
        }
    }

    private fun PipelineContext<FinalContent.ChannelContent>.checkIfRangeHeader(call: ApplicationCall): Boolean {
        val versions = subject.versions()
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


    private fun PipelineContext<FinalContent.ChannelContent>.processRange(call: ApplicationCall, rangesSpecifier: RangesSpecifier, length: Long, config: Configuration): Nothing {
        require(length >= 0L)

        val merged = rangesSpecifier.merge(length, config.maxRangeCount)
        if (merged.isEmpty()) {
            call.response.contentRange(range = null, fullLength = length) // https://tools.ietf.org/html/rfc7233#section-4.4
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable.description("Couldn't satisfy range request $rangesSpecifier: it should comply with the restriction [0; $length)"))
        }

        val channel = subject.channel()
        onFail { channel.close() }
        onSuccess { channel.close() }

        if (merged.size != 1 && !merged.isAscending() && channel !is SeekableAsyncChannel) {
            // merge into single range for non-seekable channel
            processSingleRange(call, channel, rangesSpecifier.mergeToSingle(length)!!, length)
        }

        if (merged.size == 1) {
            processSingleRange(call, channel, merged.single(), length)
        }

        processMultiRange(call, channel, merged, length)
    }

    private fun PipelineContext<FinalContent.ChannelContent>.processSingleRange(call: ApplicationCall, channel: AsyncReadChannel, range: LongRange, length: Long): Nothing {
        if (call.isGet()) {
            call.response.status(HttpStatusCode.PartialContent) // TODO status should be in a response object
        }

        call.respond(RangeChannelProvider.Single(subject.headers, channel, range, length))
    }

    private fun PipelineContext<FinalContent.ChannelContent>.processMultiRange(call: ApplicationCall, channel: AsyncReadChannel, ranges: List<LongRange>, length: Long): Nothing {
        val boundary = "ktor-boundary-" + nextNonce()
        if (call.isGet()) {
            call.response.status(HttpStatusCode.PartialContent)
        }
        call.attributes.put(CompressionAttributes.preventCompression, true) // multirange with compression is not supported yet

        val contentType = subject.contentType() ?: ContentType.Application.OctetStream
        call.respond(RangeChannelProvider.Multiple(subject.headers, channel, ranges, length, boundary, contentType))
    }

    private sealed class RangeChannelProvider : FinalContent.ChannelContent() {
        class ByPass(val delegate: FinalContent.ChannelContent) : RangeChannelProvider() {
            override fun channel() = delegate.channel()

            override val headers: ValuesMap
                get() = ValuesMap.build(true) {
                    appendAll(delegate.headers)
                    acceptRanges()
                }
        }

        class Single(val delegateHeaders: ValuesMap, val delegate: AsyncReadChannel, val range: LongRange, val fullLength: Long) : RangeChannelProvider() {
            override fun channel() = when (delegate) {
                is SeekableAsyncChannel -> AsyncSeekAndCut(delegate, range.start, range.length, preventClose = true)
                else -> AsyncSkipAndCut(delegate, range.start, range.length, preventClose = true)
            }

            override val headers: ValuesMap
                get() = ValuesMap.build(true) {
                    appendAll(delegateHeaders)
                    acceptRanges()
                    contentRange(range, fullLength)
                    noContentLength()
                }
        }

        class Multiple(val delegateHeaders: ValuesMap, val delegate: AsyncReadChannel, val ranges: List<LongRange>, val length: Long, val boundary: String, val contentType: ContentType) : RangeChannelProvider() {
            override fun channel() = ByteRangesChannel.forRegular(ranges, delegate, length, boundary, contentType.toString())

            override val headers: ValuesMap
                get() = ValuesMap.build(true) {
                    appendAll(delegateHeaders)
                    acceptRanges()
                    noContentLength()

                    remove(HttpHeaders.ContentType)
                    append(HttpHeaders.ContentType, ContentType.MultiPart.ByteRanges.withParameter("boundary", boundary).toString())
                }
        }

        protected fun ValuesMapImpl.Builder.acceptRanges() {
            append(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)
        }

        protected fun ValuesMapImpl.Builder.contentRange(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes.unitToken) {
            append(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
        }

        protected fun ValuesMapImpl.Builder.noContentLength() {
            remove(HttpHeaders.ContentLength)
        }

    }

    private fun ApplicationCall.isGet() = request.httpMethod == HttpMethod.Get
    private fun ApplicationCall.isGetOrHead() = isGet() || request.httpMethod == HttpMethod.Head
    private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()
}
