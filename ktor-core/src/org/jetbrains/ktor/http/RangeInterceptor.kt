package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.nio.channels.*
import kotlin.properties.*

class RangeInterceptor : ApplicationFeature<RangeInterceptor.RangesConfig> {
    override val name = "Ranges support"
    override val key: AttributeKey<RangesConfig> = AttributeKey(name)

    class RangesConfig {
        var maxRangeCount: Int by Delegates.vetoable(10) { p, old, new ->
            new <= 0 || throw IllegalArgumentException("Bad maxRangeCount value $new")
        }
    }

    override fun install(application: Application, configure: RangesConfig.() -> Unit): RangesConfig {
        val config = RangesConfig()
        configure(config)

        application.intercept { requestNext ->
            val rangeSpecifier = request.ranges()
            if (rangeSpecifier != null && isGetOrHead()) {
                response.interceptSend { obj, next ->
                    if (obj is HasContentLength && obj is ChannelContentProvider && obj.seekable && obj !is RangeChannelProvider) {
                        processIfRangeHeader(obj, obj.contentLength, rangeSpecifier, config, next)
                    } else {
                        next(obj)
                    }
                }
            } else {
                response.interceptSend { obj, next ->
                    if (obj is HasContentLength && obj is ChannelContentProvider && obj.seekable && obj !is RangeChannelProvider) {
                        response.headers.append(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)
                    }

                    next(obj)
                }
            }

            requestNext()
        }

        return config
    }

    private fun ApplicationCall.processIfRangeHeader(obj: ChannelContentProvider, length: Long, rangeSpecifier: RangesSpecifier, config: RangesConfig, next: (Any) -> ApplicationCallResult): ApplicationCallResult {
        val version = (obj as? HasVersions)?.version
        val ifRange = request.header(HttpHeaders.IfRange)

        val unchanged = ifRange == null || when (version) {
            is EntityTagVersion -> version.etag in ifRange.parseMatchTag()
            is LastModifiedVersion -> version.lastModified <= ifRange.fromHttpDateString().toLocalDateTime()
            else -> true
        }

        return if (unchanged) {
            val contentType = (obj as? HasContentType)?.contentType ?: ContentType.Application.OctetStream
            processRange(obj, rangeSpecifier, length, contentType, config, next)
        } else {
            next(obj)
        }
    }

    private fun ApplicationCall.processRange(obj: ChannelContentProvider, rangesSpecifier: RangesSpecifier, length: Long, contentType: ContentType, config: RangesConfig, next: (Any) -> ApplicationCallResult): ApplicationCallResult {
        require(length >= 0L)

        val merged = rangesSpecifier.merge(length, config.maxRangeCount)
        if (merged.isEmpty()) {
            response.contentRange(range = null, fullLength = length) // https://tools.ietf.org/html/rfc7233#section-4.4
            return response.sendError(HttpStatusCode.RequestedRangeNotSatisfiable, "Couldn't satisfy range request $rangesSpecifier: it should comply restriction [0; $length)")
        }

        val boundary = "ktor-boundary-" + nextNonce()
        if (merged.size == 1) {
            val single = merged.single()
            response.contentRange(range = merged.single(), fullLength = length)
            if (single == 0L..length - 1) {
                response.status(HttpStatusCode.PartialContent)
                return next(obj)
            }
        } else if (merged.size != 1 && !obj.seekable) {
            return next(obj)
        } else {
            attributes.put(CompressionAttributes.preventCompression, true)
            response.contentType(ContentType.MultiPart.ByteRanges.withParameter("boundary", boundary))
        }

        response.status(HttpStatusCode.PartialContent)
        return next(RangeChannelProvider(obj, merged, boundary, contentType))
    }

    private class RangeChannelProvider(val obj: ChannelContentProvider, val merged: List<LongRange>, val boundary: String, val contentType: ContentType) : ChannelContentProvider {
        override fun channel(): AsynchronousByteChannel {
            val delegate = obj.channel()
            require(delegate.isOpen)

            return when {
                merged.size == 1 -> {
                    val singleRange = merged.single()
                    when (delegate) {
                        is StatefulAsyncFileChannel -> StatefulAsyncFileChannel(delegate.fc, delegate.range.subRange(singleRange))
                        else -> delegate.cut(singleRange.start, singleRange.endInclusive)
                    }
                }
                delegate is StatefulAsyncFileChannel -> {
                    ByteRangesChannel(ByteRangesChannel.ChannelWithRange(delegate.fc, delegate.range), merged, boundary, contentType.toString())
                }
                else -> {
                    TODO() // TODO how do I handle this? should be 200 OK
                }
            }
        }

        override val seekable: Boolean
            get() = false
    }

    private fun ApplicationCall.isGetOrHead() = request.httpMethod == HttpMethod.Get || request.httpMethod == HttpMethod.Head
    private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()
}
fun LongRange.subRange(sub: LongRange) = start + sub.start .. (start + sub.start + sub.length - 1).coerceAtMost(endInclusive)
