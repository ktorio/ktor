package io.ktor.features

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import java.time.*
import java.util.*

/**
 * Feature to check modified/match conditional headers and avoid sending contents if it was not changed
 */
class ConditionalHeaders {
    /**
     * `ApplicationFeature` implementation for [ConditionalHeaders]
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Unit, ConditionalHeaders> {
        override val key = AttributeKey<ConditionalHeaders>("Conditional Headers")
        override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit): ConditionalHeaders {
            configure(Unit)
            val feature = ConditionalHeaders()
            // Intercept response pipeline and after the content is ready to be served
            // check if it needs to be served according to conditions
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
                // Check if any conditional header is present
                if (!headers.any { it in call.request.headers })
                    return@intercept

                val status = when (message) {
                    is Resource -> checkVersions(call, message.versions)
                    is OutgoingContent -> checkVersions(call, message.lastModifiedAndEtagVersions())
                    else -> VersionCheckResult.OK
                }
                if (status != VersionCheckResult.OK) {
                    val response = HttpStatusCodeContent(status.statusCode)
                    proceedWith(response)
                }
            }

            return feature
        }

        private val headers = listOf(
                HttpHeaders.IfModifiedSince,
                HttpHeaders.IfUnmodifiedSince,
                HttpHeaders.IfMatch,
                HttpHeaders.IfNoneMatch
        )

        private suspend fun checkVersions(call: ApplicationCall, versions: List<Version>): VersionCheckResult {
            for (version in versions) {
                val result = version.check(call)
                if (result != VersionCheckResult.OK) {
                    return result
                }
            }
            return VersionCheckResult.OK
        }
    }
}

/**
 * Checks current [etag] value and pass it through conditions supplied by the remote client. Depends on conditions it
 * produces 410 Precondition Failed or 304 Not modified responses when necessary.
 * Otherwise sets ETag header and delegates to the [block] function
 *
 * It never handles If-None-Match: *  as it is related to non-etag logic (for example, Last modified checks).
 * See http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26 for more details
 */
suspend fun ApplicationCall.withETag(etag: String, putHeader: Boolean = true, block: suspend () -> Unit) {
    val version = EntityTagVersion(etag)
    val result = version.check(this)
    if (putHeader) {
        // TODO: use version.appendHeader
        response.header(HttpHeaders.ETag, etag)
    }
    when (result) {
        VersionCheckResult.NOT_MODIFIED,
        VersionCheckResult.PRECONDITION_FAILED -> respond(result.statusCode)
        VersionCheckResult.OK -> block()
    }
}

suspend fun ApplicationCall.withLastModified(lastModified: Date, putHeader: Boolean = true, block: suspend () -> Unit) {
    withLastModified(LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()), putHeader, block)
}

suspend fun ApplicationCall.withLastModified(lastModified: ZonedDateTime, putHeader: Boolean = true, block: suspend () -> Unit) {
    withLastModified(lastModified.toLocalDateTime(), putHeader, block)
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
suspend fun ApplicationCall.withLastModified(lastModified: LocalDateTime, putHeader: Boolean = true, block: suspend () -> Unit) {
    val version = LastModifiedVersion(lastModified)
    val result = version.check(this)

    if (putHeader) {
        // TODO: use version.appendHeader
        response.header(HttpHeaders.LastModified, lastModified)
    }

    return when (result) {
        VersionCheckResult.NOT_MODIFIED,
        VersionCheckResult.PRECONDITION_FAILED -> respond(result.statusCode)
        VersionCheckResult.OK -> block()
    }
}

