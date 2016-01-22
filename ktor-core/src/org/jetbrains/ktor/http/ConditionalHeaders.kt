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
fun ApplicationCall.withETag(etag: String, block: () -> ApplicationCallResult): ApplicationCallResult {

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

    response.header(HttpHeaders.ETag, etag)

    return block()
}

fun ApplicationCall.withLastModified(lastModified: Date, block: () -> ApplicationCallResult): ApplicationCallResult {
    return withLastModified(LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()), block)
}

fun ApplicationCall.withLastModified(lastModified: ZonedDateTime, block: () -> ApplicationCallResult): ApplicationCallResult {
    return withLastModified(lastModified.toLocalDateTime(), block)
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
fun ApplicationCall.withLastModified(lastModified: LocalDateTime, block: () -> ApplicationCallResult): ApplicationCallResult {
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

    return block()
}

private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()
