package io.ktor.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import java.time.*
import java.util.*

/**
 * Feature to check modified/match conditional headers and avoid sending contents if it was not changed
 */
class ConditionalHeaders(private val versionProviders: List<suspend (OutgoingContent) -> List<Version>>) {

    /**
     * Configuration for [ConditionalHeaders] feature
     */
    class Configuration {
        internal val versionProviders = mutableListOf<suspend (OutgoingContent) -> List<Version>>()

        init {
            versionProviders.add { content -> content.defaultVersions }
        }

        /**
         * Registers a function that can fetch version list for a given [OutgoingContent]
         */
        fun version(provider: suspend (OutgoingContent) -> List<Version>) {
            versionProviders.add(provider)
        }
    }

    internal suspend fun interceptor(context: PipelineContext<Any, ApplicationCall>, message: Any) {
        val call = context.call

        val versions = if (message is OutgoingContent) versionsFor(message) else emptyList()

        val checkResult = checkVersions(call, versions)
        if (checkResult != VersionCheckResult.OK) {
            val response = HttpStatusCodeContent(checkResult.statusCode)
            context.proceedWith(response)
            return
        }

        if (versions.isNotEmpty()) {
            val headers = Headers.build {
                versions.forEach { it.appendHeadersTo(this) }
            }

            val responseHeaders = call.response.headers
            headers.forEach { name, values ->
                values.forEach { responseHeaders.append(name, it) }
            }
        }
    }

    private fun checkVersions(call: ApplicationCall, versions: List<Version>): VersionCheckResult {
        for (version in versions) {
            val result = version.check(call)
            if (result != VersionCheckResult.OK) {
                return result
            }
        }
        return VersionCheckResult.OK
    }

    /**
     * Retrieves versions such as [LastModifiedVersion] or [EntityTagVersion] for a given content
     */
    suspend fun versionsFor(content: OutgoingContent): List<Version> {
        return versionProviders.flatMapTo(ArrayList(versionProviders.size)) { it(content) }
    }

    /**
     * `ApplicationFeature` implementation for [ConditionalHeaders]
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, ConditionalHeaders> {
        override val key = AttributeKey<ConditionalHeaders>("Conditional Headers")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ConditionalHeaders {
            val configuration = Configuration().apply(configure)
            val feature = ConditionalHeaders(configuration.versionProviders)

            // Intercept response pipeline and after the content is ready to be served
            // check if it needs to be served according to conditions
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { message -> feature.interceptor(this, message) }

            return feature
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
@Deprecated("Use configuration for ConditionalHeaders")
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
@Deprecated("Use configuration for ConditionalHeaders")
suspend fun ApplicationCall.withLastModified(lastModified: ZonedDateTime, putHeader: Boolean = true, block: suspend () -> Unit) {
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

/**
 * Retrieves LastModified and ETag versions from this [OutgoingContent] headers
 */
val OutgoingContent.defaultVersions: List<Version>
    get() {
        val extensionVersions = versions
        if (extensionVersions.isNotEmpty())
            return extensionVersions

        val headers = headers
        val lastModifiedHeaders = headers.getAll(HttpHeaders.LastModified) ?: emptyList()
        val etagHeaders = headers.getAll(HttpHeaders.ETag) ?: emptyList()
        val versions = ArrayList<Version>(lastModifiedHeaders.size + etagHeaders.size)
        lastModifiedHeaders.mapTo(versions) { LastModifiedVersion(ZonedDateTime.parse(it, httpDateFormat)) }
        etagHeaders.mapTo(versions) { EntityTagVersion(it) }
        return versions
    }
