/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Feature to check modified/match conditional headers and avoid sending contents if it was not changed
 */
public class ConditionalHeaders(private val versionProviders: List<suspend (OutgoingContent) -> List<Version>>) {

    /**
     * Configuration for [ConditionalHeaders] feature
     */
    public class Configuration {
        internal val versionProviders = mutableListOf<suspend (OutgoingContent) -> List<Version>>()

        init {
            versionProviders.add { content -> content.defaultVersions }
        }

        /**
         * Registers a function that can fetch version list for a given [OutgoingContent]
         */
        public fun version(provider: suspend (OutgoingContent) -> List<Version>) {
            versionProviders.add(provider)
        }
    }

    internal suspend fun interceptor(context: PipelineContext<Any, ApplicationCall>, message: Any) {
        val call = context.call

        val versions = if (message is OutgoingContent) versionsFor(message) else emptyList()

        if (versions.isNotEmpty()) {
            val headers = Headers.build {
                versions.forEach { it.appendHeadersTo(this) }
            }

            val responseHeaders = call.response.headers
            headers.forEach { name, values ->
                values.forEach { responseHeaders.append(name, it) }
            }
        }

        val checkResult = checkVersions(call, versions)
        if (checkResult != VersionCheckResult.OK) {
            val response = HttpStatusCodeContent(checkResult.statusCode)
            context.proceedWith(response)
            return
        }
    }

    private fun checkVersions(call: ApplicationCall, versions: List<Version>): VersionCheckResult {
        for (version in versions) {
            val result = version.check(call.request.headers)
            if (result != VersionCheckResult.OK) {
                return result
            }
        }
        return VersionCheckResult.OK
    }

    /**
     * Retrieves versions such as [LastModifiedVersion] or [EntityTagVersion] for a given content
     */
    public suspend fun versionsFor(content: OutgoingContent): List<Version> {
        return versionProviders.flatMapTo(ArrayList(versionProviders.size)) { it(content) }
    }

    /**
     * `ApplicationFeature` implementation for [ConditionalHeaders]
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, ConditionalHeaders> {
        override val key: AttributeKey<ConditionalHeaders> = AttributeKey<ConditionalHeaders>("Conditional Headers")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): ConditionalHeaders {
            val configuration = Configuration().apply(configure)
            val feature = ConditionalHeaders(configuration.versionProviders)

            // Intercept response pipeline and after the content is ready to be served
            // check if it needs to be served according to conditions
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
                feature.interceptor(
                    this,
                    message
                )
            }

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
@Deprecated("Use configuration for ConditionalHeaders or configure block of call.respond function.")
public suspend fun ApplicationCall.withETag(etag: String, putHeader: Boolean = true, block: suspend () -> Unit) {
    val version = EntityTagVersion(etag)
    val result = version.check(request.headers)
    if (putHeader) {
        response.header(HttpHeaders.ETag, etag)
    }
    when (result) {
        VersionCheckResult.NOT_MODIFIED,
        VersionCheckResult.PRECONDITION_FAILED -> respond(result.statusCode)
        VersionCheckResult.OK -> block()
    }
}

/**
 * Retrieves LastModified and ETag versions from this [OutgoingContent] headers
 */
@Deprecated("Use versions or headers.parseVersions()")
public val OutgoingContent.defaultVersions: List<Version>
    get() {
        val extensionVersions = versions
        if (extensionVersions.isNotEmpty()) {
            return extensionVersions
        }

        return headers.parseVersions()
    }

/**
 * Retrieves LastModified and ETag versions from headers.
 */
public fun Headers.parseVersions(): List<Version> {
    val lastModifiedHeaders = getAll(HttpHeaders.LastModified) ?: emptyList()
    val etagHeaders = getAll(HttpHeaders.ETag) ?: emptyList()
    val versions = ArrayList<Version>(lastModifiedHeaders.size + etagHeaders.size)

    lastModifiedHeaders.mapTo(versions) {
        LastModifiedVersion(it.fromHttpToGmtDate())
    }
    etagHeaders.mapTo(versions) { EntityTagVersion(it) }

    return versions
}
