package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*

/**
 * Checks current [etag] value and pass it through conditions supplied by the remote client. Depends on conditions it
 * produces 410 Precondition Failed or 304 Not modified responses when necessary.
 * Otherwise sets ETag header and delegates to the [block] function
 *
 * It never handles If-None-Match: *  as it is related to non-etag logic (for example, Last modified checks).
 * See http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26 for more details
 */
fun ApplicationRequestContext.withETag(etag: String, block: () -> ApplicationRequestStatus): ApplicationRequestStatus {

    val givenNoneMatchEtags = request.header(HttpHeaders.IfNoneMatch)?.parseMatchTag()
    val givenMatchEtags = request.header(HttpHeaders.IfMatch)?.parseMatchTag()

    if (givenNoneMatchEtags != null && etag in givenNoneMatchEtags && "*" !in givenNoneMatchEtags) {
        response.status(HttpStatusCode.NotModified)
        return ApplicationRequestStatus.Handled
    }

    if (givenMatchEtags != null && givenMatchEtags.isNotEmpty() && etag !in givenMatchEtags && "*" !in givenMatchEtags) {
        response.status(HttpStatusCode.PreconditionFailed)
        return ApplicationRequestStatus.Handled
    }

    response.header(HttpHeaders.ETag, etag)

    return block()
}

private fun String.parseMatchTag() = split("\\s*,\\s*".toRegex()).map { it.removePrefix("W/") }.filter { it.isNotEmpty() }.toSet()
