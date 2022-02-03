/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.conditionalheaders

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * A configuration for the [ConditionalHeaders] plugin
 */
@KtorDsl
public class ConditionalHeadersConfig {
    internal val versionProviders = mutableListOf<suspend (OutgoingContent) -> List<Version>>()

    init {
        versionProviders.add { content -> content.versions }
        versionProviders.add { content -> content.headers.parseVersions() }
    }

    /**
     * Registers a function that can fetch version list for a given [OutgoingContent]
     */
    public fun version(provider: suspend (OutgoingContent) -> List<Version>) {
        versionProviders.add(provider)
    }
}

internal val ConditionalHeadersKey: AttributeKey<List<suspend (OutgoingContent) -> List<Version>>> =
    AttributeKey("ConditionalHeadersKey")

/**
 * Retrieves versions such as [LastModifiedVersion] or [EntityTagVersion] for a given content
 */
public suspend fun ApplicationCall.versionsFor(content: OutgoingContent): List<Version> {
    val versionProviders = application.attributes.getOrNull(ConditionalHeadersKey)
    return versionProviders?.flatMapTo(ArrayList(versionProviders.size)) { it(content) } ?: emptyList()
}

/**
 * A plugin that avoids sending the body of content if it has not changed since the last request.
 */
public val ConditionalHeaders: RouteScopedPlugin<ConditionalHeadersConfig, PluginInstance> = createRouteScopedPlugin(
    "ConditionalHeaders",
    ::ConditionalHeadersConfig
) {
    val versionProviders = pluginConfig.versionProviders

    application.attributes.put(ConditionalHeadersKey, versionProviders)

    fun checkVersions(call: ApplicationCall, versions: List<Version>): VersionCheckResult {
        for (version in versions) {
            val result = version.check(call.request.headers)
            if (result != VersionCheckResult.OK) {
                return result
            }
        }
        return VersionCheckResult.OK
    }

    onCallRespond.afterTransform { call, message ->
        val versions = call.versionsFor(message)

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
            transformBody {
                HttpStatusCodeContent(checkResult.statusCode)
            }
            return@afterTransform
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
@Deprecated(
    "Use configuration for ConditionalHeaders or configure block of call.respond function.",
    level = DeprecationLevel.ERROR
)
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
@Deprecated(
    "Use versions or headers.parseVersions()",
    level = DeprecationLevel.ERROR
)
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
