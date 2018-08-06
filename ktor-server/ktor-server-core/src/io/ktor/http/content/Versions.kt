package io.ktor.http.content

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import java.nio.file.attribute.*
import java.time.*
import java.util.*

/**
 * Specifies a key for VersionList extension property for [OutgoingContent]
 */
val VersionListProperty = AttributeKey<List<Version>>("VersionList")

/**
 * Gets or sets list of [Version] instances as an extension property on this content
 */
var OutgoingContent.versions: List<Version>
    get() = getProperty(VersionListProperty) ?: emptyList()
    set(value) = setProperty(VersionListProperty, value)

/**
 * Represents content version
 *
 * An example of version is [EntityTagVersion] or [LastModifiedVersion]
 */
interface Version {
    /**
     * Checks [call] against this version and returns [VersionCheckResult]
     */
    fun check(call: ApplicationCall): VersionCheckResult

    /**
     * Appends relevant headers to the builder
     */
    fun appendHeadersTo(builder: HeadersBuilder)
}

/**
 * Represent result of the version comparison between content being sent and HTTP request.
 *
 * @param statusCode represents [HttpStatusCode] associated with the result.
 */
enum class VersionCheckResult(val statusCode: HttpStatusCode) {
    /**
     * Indicates that content needs to be sent to recipient.
     */
    OK(HttpStatusCode.OK),

    /**
     * Indicates that content has not modified according to headers sent by client.
     */
    NOT_MODIFIED(HttpStatusCode.NotModified),

    /**
     * One or more conditions given in the request header fields evaluated to false.
     */
    PRECONDITION_FAILED(HttpStatusCode.PreconditionFailed)
}

/**
 * This version passes the given [lastModified] date through the client provided
 * http conditional headers If-Modified-Since and If-Unmodified-Since.
 *
 * Notice the second precision so it may work wrong if there were few changes during the same second.
 *
 * For better behaviour use etag instead
 *
 * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.28 and
 *  https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25
 *
 *  @param lastModified of the current content, for example file's last modified date
 */
data class LastModifiedVersion(val lastModified: ZonedDateTime) : Version {
    /**
     *  @return [VersionCheckResult.OK] if all header pass or there was no headers in the request,
     *  [VersionCheckResult.NOT_MODIFIED] for If-Modified-Since,
     *  [VersionCheckResult.PRECONDITION_FAILED] for If-Unmodified*Since
     */
    override fun check(call: ApplicationCall): VersionCheckResult {
        val normalized = lastModified.withNano(0) // we need this because of the http date format that only has seconds
        val ifModifiedSince = call.request.headers[HttpHeaders.IfModifiedSince]?.fromHttpDateString()
        val ifUnmodifiedSince = call.request.headers[HttpHeaders.IfUnmodifiedSince]?.fromHttpDateString()

        if (ifModifiedSince != null) {
            if (normalized <= ifModifiedSince) {
                return VersionCheckResult.NOT_MODIFIED
            }
        }
        if (ifUnmodifiedSince != null) {
            if (normalized > ifUnmodifiedSince) {
                return VersionCheckResult.PRECONDITION_FAILED
            }
        }

        return VersionCheckResult.OK
    }

    constructor(lastModified: FileTime) : this(ZonedDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()))
    constructor(lastModified: Date) : this(lastModified.toZonedDateTime())

    override fun appendHeadersTo(builder: HeadersBuilder) {
        builder.lastModified(lastModified.withZoneSameInstant(GreenwichMeanTime))
    }
}

/**
 * This version checks [etag] value and pass it through conditions supplied by the remote client. Depending on conditions it
 * produces return value of enum type [VersionCheckResult]
 *
 * It never handles If-None-Match: *  as it is related to non-etag logic (for example, Last modified checks).
 * See http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26 for more details
 *
 * @param etag - entity tag, for example file's content hash
 * @return [VersionCheckResult.OK] if all headers pass or there was no related headers,
 * [VersionCheckResult.NOT_MODIFIED] for successful If-None-Match,
 * [VersionCheckResult.PRECONDITION_FAILED] for failed If-Match
 */
data class EntityTagVersion(val etag: String) : Version {
    override fun check(call: ApplicationCall): VersionCheckResult {
        val givenNoneMatchEtags = call.request.header(HttpHeaders.IfNoneMatch)?.parseMatchTag()
        val givenMatchEtags = call.request.header(HttpHeaders.IfMatch)?.parseMatchTag()

        if (givenNoneMatchEtags != null && etag in givenNoneMatchEtags && "*" !in givenNoneMatchEtags) {
            return VersionCheckResult.NOT_MODIFIED
        }

        if (givenMatchEtags != null && givenMatchEtags.isNotEmpty() && etag !in givenMatchEtags && "*" !in givenMatchEtags) {
            return VersionCheckResult.PRECONDITION_FAILED
        }

        return VersionCheckResult.OK
    }

    private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()

    override fun appendHeadersTo(builder: HeadersBuilder) {
        builder.etag(etag)
    }
}

