package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.time.*
import java.util.*

/**
 * Checks current [etag] value and pass it through conditions supplied by the remote client. Depends on conditions it
 * produces 410 Precondition Failed or 304 Not modified responses when necessary.
 * Otherwise sets ETag header and delegates to the [block] function
 *
 * It never handles If-None-Match: *  as it is related to non-etag logic (for example, Last modified checks).
 * See http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26 for more details
 */
fun ApplicationCall.withETag(etag: String, putHeader: Boolean = true, block: () -> ApplicationCallResult): ApplicationCallResult {

    val givenNoneMatchEtags = request.header(HttpHeaders.IfNoneMatch)?.parseMatchTag()
    val givenMatchEtags = request.header(HttpHeaders.IfMatch)?.parseMatchTag()

    if (givenNoneMatchEtags != null && etag in givenNoneMatchEtags && "*" !in givenNoneMatchEtags) {
        response.status(HttpStatusCode.NotModified)
        return ApplicationCallResult.Handled
    }

    if (givenMatchEtags != null && givenMatchEtags.isNotEmpty() && etag !in givenMatchEtags && "*" !in givenMatchEtags) {
        response.status(HttpStatusCode.PreconditionFailed)
        return ApplicationCallResult.Handled
    }

    if (putHeader) {
        response.header(HttpHeaders.ETag, etag)
    }

    return block()
}

fun ApplicationCall.withLastModified(lastModified: Date, putHeader: Boolean = true, block: () -> ApplicationCallResult): ApplicationCallResult {
    return withLastModified(LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()), putHeader, block)
}

fun ApplicationCall.withLastModified(lastModified: ZonedDateTime, putHeader: Boolean = true, block: () -> ApplicationCallResult): ApplicationCallResult {
    return withLastModified(lastModified.toLocalDateTime(), putHeader, block)
}

/**
 * The function passes the given [lastModified] date through the client provided
 *  http conditional headers If-Modified-Since and If-Unmodified-Since. Depends on conditions it
 * produces 410 Precondition Failed or 304 Not modified responses when necessary.
 * Otherwise sets ETag header and delegates to the [block] function.
 *
 * Notice the second precision so it may work wrong if there were few changes during the same second.
 *
 * For better behaviour use etag instead
 *
 * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.28 and
 *  https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25
 */
fun ApplicationCall.withLastModified(lastModified: LocalDateTime, putHeader: Boolean = true, block: () -> ApplicationCallResult): ApplicationCallResult {
    val normalized = lastModified.withNano(0) // we need this because of the http date format that only has seconds
    val ifModifiedSince = request.headers[HttpHeaders.IfModifiedSince]?.let { it.fromHttpDateString().toLocalDateTime() }
    val ifUnmodifiedSince = request.headers[HttpHeaders.IfUnmodifiedSince]?.let { it.fromHttpDateString().toLocalDateTime() }

    if (ifModifiedSince != null) {
        if (normalized <= ifModifiedSince) {
            response.status(HttpStatusCode.NotModified)
            return ApplicationCallResult.Handled
        }
    }
    if (ifUnmodifiedSince != null) {
        if (normalized > ifUnmodifiedSince) {
            response.status(HttpStatusCode.PreconditionFailed)
            return ApplicationCallResult.Handled
        }
    }

    if (putHeader) {
        response.header(HttpHeaders.LastModified, lastModified)
    }

    return block()
}

fun ApplicationCall.withIfRange(date: Date, block: (PartialContentRange?) -> ApplicationCallResult): ApplicationCallResult {
    return withIfRange(date.toDateTime().toLocalDateTime(), block)
}

fun ApplicationCall.withIfRange(lastModified: ZonedDateTime, block: (PartialContentRange?) -> ApplicationCallResult): ApplicationCallResult {
    return withIfRange(lastModified.toLocalDateTime(), block)
}

/**
 * Checks for If-Range request header that could contain last modified date and calls [block] with the corresponding
 * range if you should respond with partial content or with `null` if you should respond with full content.
 * It also set response status code to 206 Partial Content or 200 OK when necessary.
 *
 * Notice that is will put Last-Modified response header
 *
 *  See https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.27
 */
fun ApplicationCall.withIfRange(lastModified: LocalDateTime, block: (PartialContentRange?) -> ApplicationCallResult): ApplicationCallResult {
    val normalized = lastModified.withNano(0)
    val range = request.header(HttpHeaders.Range)?.let { parseRangesSpecifier(it) }
    val ifRange = request.header(HttpHeaders.IfRange)?.let { it.fromHttpDateString().toLocalDateTime() }

    val rangeToProcess = when {
        range == null -> null
        ifRange == null -> range
        normalized > ifRange -> null
        else -> range
    }

    if (rangeToProcess != null) {
        response.status(HttpStatusCode.PartialContent)
    }

    return withLastModified(lastModified) {
        block(rangeToProcess)
    }
}

/**
 * Checks for If-Range request header that could contain ETag and calls [block] with the corresponding
 * range if you should respond with partial content or with `null` if you should respond with full content.
 * It also set response status code to 206 Partial Content or 200 OK when necessary.
 *
 * Notice that is will put ETag response header
 *
 *  See https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.27
 */
fun ApplicationCall.withIfRange(entity: String, block: (PartialContentRange?) -> ApplicationCallResult): ApplicationCallResult {
    val range = request.header(HttpHeaders.Range)?.let { parseRangesSpecifier(it) }
    val ifRange = request.header(HttpHeaders.IfRange)?.let { it.parseMatchTag() }

    val rangeToProcess = when {
        range == null -> null
        ifRange == null -> range
        entity !in ifRange -> null
        else -> range
    }

    if (rangeToProcess != null) {
        response.status(HttpStatusCode.PartialContent)
    }

    return withETag(entity) {
        block(rangeToProcess)
    }
}

private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()
private fun Date.toDateTime() = ZonedDateTime.ofInstant(toInstant(), ZoneId.systemDefault())!!
