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
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.webjars.*
import java.io.*
import java.nio.file.*
import java.time.*

@Suppress("KDocMissingDocumentation")
@KtorExperimentalAPI
class Webjars(private val configuration: Configuration) {

    private fun fileName(path: String): String = Paths.get(path).fileName?.toString() ?: ""

    private fun extractWebJar(path: String): String {
        val firstDelimiter = if (path.startsWith("/")) 1 else 0
        val nextDelimiter = path.indexOf("/", 1)
        val webjar = if (nextDelimiter > -1) path.substring(firstDelimiter, nextDelimiter) else ""
        val partialPath = path.substring(nextDelimiter + 1)
        return locator.getFullPath(webjar, partialPath)
    }

    private val locator = WebJarAssetLocator()
    private val lastModified = ZonedDateTime.now(configuration.zone)

    @KtorExperimentalAPI
    class Configuration {
        var path: String = "/webjars/"
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

        var zone: ZoneId = ZoneId.systemDefault()
    }

    private suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val fullPath = context.call.request.path()
        if (fullPath.startsWith(configuration.path)
            && context.call.request.httpMethod == HttpMethod.Get
            && fileName(fullPath).isNotEmpty()
        ) {
            val resourcePath = fullPath.removePrefix(configuration.path)
            try {
                val location = extractWebJar(resourcePath)
                val stream = Webjars::class.java.classLoader.getResourceAsStream(location) ?: return
                context.call.respond(
                    InputStreamContent(
                        stream,
                        ContentType.defaultForFilePath(fileName(fullPath)),
                        lastModified
                    )
                )
            } catch (multipleFiles: MultipleMatchesException) {
                context.call.respond(HttpStatusCode.InternalServerError)
            } catch (notFound: IllegalArgumentException) {
            }
        }
    }

    @KtorExperimentalAPI
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Webjars> {

        override val key: AttributeKey<Webjars> = AttributeKey("Webjars")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Webjars {
            val configuration = Configuration().apply(configure)

            val feature = Webjars(configuration)

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
    lastModified: ZonedDateTime
) : OutgoingContent.ReadChannelContent() {
    init {
        versions += LastModifiedVersion(lastModified)
    }

    override fun readFrom(): ByteReadChannel = input.toByteReadChannel(pool = KtorDefaultPool)
}
