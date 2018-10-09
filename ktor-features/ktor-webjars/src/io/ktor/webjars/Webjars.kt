package io.ktor.webjars

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.content.LastModifiedVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.versions
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.pipeline.PipelineContext
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.io.*
import org.webjars.MultipleMatchesException
import org.webjars.WebJarAssetLocator
import java.io.InputStream
import java.nio.file.Paths
import java.time.ZoneId
import java.time.ZonedDateTime

@Suppress("KDocMissingDocumentation")
@KtorExperimentalAPI
class Webjars(val configuration: Configuration) {

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
        var path = "/webjars/"
            set(value) {
                var buffer = StringBuilder(value)
                if (!value.startsWith("/")) {
                    buffer.insert(0, "/")
                }
                if (!buffer.endsWith("/")) {
                    buffer.append("/")
                }
                field = buffer.toString()
            }
        var zone = ZoneId.systemDefault()
    }

    private suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val fullPath = context.call.request.uri
        val fileName = fileName(context.call.request.uri)
        if (fullPath.startsWith(configuration.path) && context.call.request.httpMethod == HttpMethod.Get && fileName.isNotEmpty()) {
            val resourcePath = fullPath.removePrefix(configuration.path)
            try {
                val location = extractWebJar(resourcePath)
                context.call.respond(InputStreamContent(Webjars::class.java.classLoader.getResourceAsStream(location), ContentType.defaultForFilePath(fileName), lastModified))
            } catch (multipleFiles: MultipleMatchesException) {
                context.call.respond(HttpStatusCode.InternalServerError)
            } catch (notFound: IllegalArgumentException) {
            }
        }
    }

    @KtorExperimentalAPI
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Webjars.Configuration, Webjars> {

        override val key = AttributeKey<Webjars>("Webjars")

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

private class InputStreamContent(val input: InputStream, override val contentType: ContentType, val lastModified: ZonedDateTime) : OutgoingContent.ReadChannelContent() {
    init {
        versions += LastModifiedVersion(lastModified)
    }

    override fun readFrom(): ByteReadChannel {
        return input.toByteReadChannel()
    }
}
