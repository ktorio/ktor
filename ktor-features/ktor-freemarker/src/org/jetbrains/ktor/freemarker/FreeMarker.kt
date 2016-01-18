package org.jetbrains.ktor.freemarker

import freemarker.template.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import java.io.*
import kotlin.reflect.*

class FreeMarkerTemplateResource internal constructor(val content: freemarker.template.Template, val model: Any, val etag: String, override val contentType: ContentType) : StreamContent, HasContentType, HasETag {
    override fun stream(out: OutputStream) {
        with(out.bufferedWriter(charset(contentType.parameter("charset") ?: "UTF-8"))) {
            content.process(model, this)
        }
    }

    override fun etag() = etag
}

class FreeMarkerContent(val templateName: String, val model: Any, val etag: String, val contentType: ContentType = ContentType.Text.Html)


fun freemarker(block: () -> Configuration = { Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS) }): TemplateEngine<FreeMarkerContent, FreeMarkerTemplateResource> =
        FreeMarkerTemplateEngine(block())

private class FreeMarkerTemplateEngine(val configuration: Configuration) : TemplateEngine<FreeMarkerContent, FreeMarkerTemplateResource> {

    override val contentClass: KClass<FreeMarkerContent>
        get() = FreeMarkerContent::class

    override fun process(content: FreeMarkerContent) = FreeMarkerTemplateResource(configuration.getTemplate(content.templateName), content.model, content.etag, content.contentType)
}
