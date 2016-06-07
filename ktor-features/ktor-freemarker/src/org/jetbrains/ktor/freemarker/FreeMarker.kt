package org.jetbrains.ktor.freemarker

import freemarker.template.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.content.Version
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.reflect.*

class FreeMarkerTemplateResource internal constructor(val content: freemarker.template.Template, val model: Any, val etag: String, override val contentType: ContentType) : FinalContent.StreamConsumer(), Resource {
    override fun stream(out: OutputStream) {
        with(out.bufferedWriter(charset(contentType.parameter("charset") ?: "UTF-8"))) {
            content.process(model, this)
        }
    }

    override val versions: List<Version>
        get() = listOf(EntityTagVersion(etag))

    override val expires = null
    override val cacheControl = null
    override val attributes = Attributes()
    override val contentLength = null

    override val headers: ValuesMap
        get() = super.headers
}

class FreeMarkerContent(val templateName: String, val model: Any, val etag: String, val contentType: ContentType = ContentType.Text.Html)


fun freemarker(block: () -> Configuration): TemplateEngine<FreeMarkerContent, FreeMarkerTemplateResource> =
        FreeMarkerTemplateEngine(block())

private class FreeMarkerTemplateEngine(val configuration: Configuration) : TemplateEngine<FreeMarkerContent, FreeMarkerTemplateResource> {

    override val contentClass: KClass<FreeMarkerContent>
        get() = FreeMarkerContent::class

    override fun process(content: FreeMarkerContent) = FreeMarkerTemplateResource(configuration.getTemplate(content.templateName), content.model, content.etag, content.contentType)
}
