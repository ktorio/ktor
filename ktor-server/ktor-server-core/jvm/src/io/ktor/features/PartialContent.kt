/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.properties.*

/**
 * Feature to support requests to specific content ranges.
 *
 * It is essential for streaming video and restarting downloads.
 *
 */
public class PartialContent(private val maxRangeCount: Int) {

    /**
     * Configuration for [PartialContent].
     */
    public class Configuration {
        /**
         * Maximum number of ranges that will be accepted from HTTP request.
         *
         * If HTTP request specifies more ranges, they will all be merged into a single range.
         */
        public var maxRangeCount: Int by Delegates.vetoable(10) { _, _, new ->
            new > 0 || throw IllegalArgumentException("Bad maxRangeCount value $new")
        }
    }

    /**
     * `ApplicationFeature` implementation for [PartialContent]
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, PartialContent> {
        private val PartialContentPhase = PipelinePhase("PartialContent")

        override val key: AttributeKey<PartialContent> = AttributeKey("Partial Content")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): PartialContent {
            val feature = PartialContent(Configuration().apply(configure).maxRangeCount)
            pipeline.intercept(ApplicationCallPipeline.Features) { feature.intercept(this) }
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
            val message = HttpStatusCode.MethodNotAllowed
                .description("Method ${call.request.local.method.value} is not allowed with range request")
            call.respond(message)
            context.finish()
            return
        }

        call.response.pipeline.registerPhase()
        call.attributes.put(Compression.SuppressionAttribute, true)
        call.response.pipeline.intercept(PartialContentPhase) response@{ message ->
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

    // RFC7233 sec 3.2
    private suspend fun checkIfRangeHeader(
        content: OutgoingContent.ReadChannelContent,
        call: ApplicationCall
    ): Boolean {
        val conditionalHeadersFeature = call.application.featureOrNull(ConditionalHeaders)
        val ifRange = try {
            call.request.headers.getAll(HttpHeaders.IfRange)
                ?.map { parseIfRangeHeader(it) }
                ?.takeIf { it.isNotEmpty() }
                ?.reduce { acc, list -> acc + list }
                ?.parseVersions()
                ?: return true
        } catch (_: Throwable) {
            return false
        }

        val versions = conditionalHeadersFeature?.versionsFor(content) ?: content.defaultVersions

        return versions.all { version ->
            when (version) {
                is LastModifiedVersion -> checkLastModified(version, ifRange)
                is EntityTagVersion -> checkEntityTags(version, ifRange)
                else -> true
            }
        }
    }

    private fun checkLastModified(actual: LastModifiedVersion, ifRange: List<Version>): Boolean {
        val actualDate = actual.lastModified.truncateToSeconds()

        return ifRange.all { condition ->
            when (condition) {
                is LastModifiedVersion -> actualDate <= condition.lastModified
                else -> true
            }
        }
    }

    private fun checkEntityTags(actual: EntityTagVersion, ifRange: List<Version>): Boolean {
        return ifRange.all { condition ->
            when (condition) {
                is EntityTagVersion -> actual.etag == condition.etag
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
            call.response.contentRange(
                range = null,
                fullLength = length
            ) // https://tools.ietf.org/html/rfc7233#section-4.4
            val statusCode = HttpStatusCode.RequestedRangeNotSatisfiable.description(
                "Couldn't satisfy range request $rangesSpecifier: " +
                    "it should comply with the restriction [0; $length)"
            )
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

    private suspend fun PipelineContext<Any, ApplicationCall>.processSingleRange(
        content: OutgoingContent.ReadChannelContent,
        range: LongRange,
        length: Long
    ) {
        proceedWith(PartialOutgoingContent.Single(call.isGet(), content, range, length))
    }

    private suspend fun PipelineContext<Any, ApplicationCall>.processMultiRange(
        content: OutgoingContent.ReadChannelContent,
        ranges: List<LongRange>,
        length: Long
    ) {
        val boundary = "ktor-boundary-" + generateNonce()

        call.attributes.put(Compression.SuppressionAttribute, true) // multirange with compression is not supported yet

        proceedWith(PartialOutgoingContent.Multiple(coroutineContext, call.isGet(), content, ranges, length, boundary))
    }

    private sealed class PartialOutgoingContent(val original: ReadChannelContent) :
        OutgoingContent.ReadChannelContent() {
        override val status: HttpStatusCode? get() = original.status
        override val contentType: ContentType? get() = original.contentType
        override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
        override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)

        class Bypass(original: ReadChannelContent) : PartialOutgoingContent(original) {

            override val contentLength: Long?
                get() = original.contentLength

            override fun readFrom() = original.readFrom()

            override val headers by lazy(LazyThreadSafetyMode.NONE) {
                Headers.build {
                    appendAll(original.headers)
                    acceptRanges()
                }
            }
        }

        class Single(
            val get: Boolean,
            original: ReadChannelContent,
            val range: LongRange,
            val fullLength: Long
        ) : PartialOutgoingContent(original) {
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

        class Multiple(
            override val coroutineContext: CoroutineContext,
            val get: Boolean,
            original: ReadChannelContent,
            val ranges: List<LongRange>,
            val length: Long,
            val boundary: String
        ) : PartialOutgoingContent(original), CoroutineScope {
            override val status: HttpStatusCode?
                get() = if (get) HttpStatusCode.PartialContent else original.status
            override val contentLength: Long? get() = null
            override val contentType: ContentType?
                get() = ContentType.MultiPart.ByteRanges.withParameter(
                    "boundary",
                    boundary
                )

            override fun readFrom() = writeMultipleRangesImpl(
                { range -> original.readFrom(range) },
                ranges,
                length,
                boundary,
                contentType.toString()
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
}

private fun List<LongRange>.isAscending(): Boolean =
    fold(true to 0L) { acc, e -> (acc.first && acc.second <= e.start) to e.start }.first

private fun parseIfRangeHeader(header: String): List<HeaderValue> {
    if (header.endsWith(" GMT")) {
        return listOf(HeaderValue(header))
    }

    return parseHeaderValue(header)
}

private fun List<HeaderValue>.parseVersions(): List<Version> = mapNotNull { field ->
    check(field.quality == 1.0) { "If-Range doesn't support quality" }
    check(field.params.isEmpty()) { "If-Range doesn't support parameters" }

    parseVersion(field.value)
}

private fun parseVersion(value: String): Version? {
    if (value.isBlank()) return null
    check(!value.startsWith("W/"))

    if (value.startsWith("\"")) {
        return EntityTagVersion.parseSingle(value)
    }

    return LastModifiedVersion(value.fromHttpToGmtDate())
}
