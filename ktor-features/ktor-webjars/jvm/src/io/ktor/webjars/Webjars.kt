/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webjars

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.webjars.*
import java.io.*
import java.time.*

/**
 * This feature listens to requests starting with the specified path prefix and responding with static content
 * packaged into webjars. A [WebJarAssetLocator] is used to look for static files.
 */
public class Webjars internal constructor(private val webjarsPrefix: String) {
    init {
        require(webjarsPrefix.startsWith("/"))
        require(webjarsPrefix.endsWith("/"))
    }

    @Deprecated("Use install(Webjars), there is no need to instantiate it directly.", level = DeprecationLevel.ERROR)
    public constructor(configuration: Configuration) : this(configuration.path)

    private val locator = WebJarAssetLocator()
    private val knownWebJars = locator.webJars?.keys?.toSet() ?: emptySet()
    private val lastModified = GMTDate()

    private fun extractWebJar(path: String): String {
        val firstDelimiter = if (path.startsWith("/")) 1 else 0
        val nextDelimiter = path.indexOf("/", 1)
        val webjar = if (nextDelimiter > -1) path.substring(firstDelimiter, nextDelimiter) else ""
        val partialPath = path.substring(nextDelimiter + 1)
        if (webjar !in knownWebJars) {
            throw IllegalArgumentException("jar $webjar not found")
        }
        return locator.getFullPath(webjar, partialPath)
    }

    /**
     * Feature configuration.
     */
    public class Configuration {
        /**
         * Path prefix at which the installed feature responds.
         */
        public var path: String = "/webjars/"
            set(value) {
                field = buildString(value.length + 2) {
                    if (!value.startsWith('/')) {
                        append('/')
                    }
                    append(value)
                    if (!endsWith('/')) {
                        append('/')
                    }
                }
            }

        /**
         * Makes no effect. Will be dropped in future releases.
         */
        @Suppress("unused")
        @Deprecated("This is no longer used and will be dropped in future releases.", level = DeprecationLevel.ERROR)
        public var zone: ZoneId = ZoneId.systemDefault()
    }

    private suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val fullPath = context.call.request.path()
        if (fullPath.startsWith(webjarsPrefix) &&
            context.call.request.httpMethod == HttpMethod.Get &&
            fullPath.last() != '/'
        ) {
            val resourcePath = fullPath.removePrefix(webjarsPrefix)
            try {
                val location = extractWebJar(resourcePath)
                val stream = Webjars::class.java.classLoader.getResourceAsStream(location) ?: return
                context.call.respond(
                    InputStreamContent(
                        stream,
                        ContentType.defaultForFilePath(fullPath),
                        lastModified
                    )
                )
            } catch (multipleFiles: MultipleMatchesException) {
                context.call.respond(HttpStatusCode.InternalServerError)
            } catch (notFound: IllegalArgumentException) {
            }
        }
    }

    /**
     * Feature installation companion.
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Webjars> {

        override val key: AttributeKey<Webjars> = AttributeKey("Webjars")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Webjars {
            val configuration = Configuration().apply(configure)

            val feature = Webjars(configuration.path)

            pipeline.intercept(ApplicationCallPipeline.Features) {
                feature.intercept(this)
            }
            return feature
        }
    }
}

private class InputStreamContent(
    val input: InputStream,
    override val contentType: ContentType,
    lastModified: GMTDate
) : OutgoingContent.ReadChannelContent() {
    init {
        versions += LastModifiedVersion(lastModified)
    }

    override fun readFrom(): ByteReadChannel = input.toByteReadChannel(pool = KtorDefaultPool)
}
