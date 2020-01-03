/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.jtwig

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.jtwig.*
import org.jtwig.environment.*
import org.jtwig.resource.reference.*


/**
 * Represents a content handled by [Jtwig] feature.
 *
 * @param template name that is resolved by Jtwig
 * @param model to be passed during template rendering
 * @param etag value for `E-Tag` header (optional)
 * @param contentType of response (optional, `text/html` with UTF-8 character encoding by default)
 */
class JtwigContent(
    val template: String,
    val model: Map<String, Any> = emptyMap(),
    val etag: String? = null,
    val contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
)

/**
 * Jtwig support feature. Provides ability to respond with [JtwigContent]
 */
class Jtwig(private val config: Configuration) {
    private val environment = EnvironmentFactory().create(
        EnvironmentConfigurationBuilder(config.configuration)
            .build()
    )

    class Configuration {
        var templatePrefix: String = ""
        var templateSuffix: String = ""
        var configuration: EnvironmentConfiguration = DefaultEnvironmentConfiguration()
    }

    /**
     * A feature installing companion object
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Jtwig> {
        override val key: AttributeKey<Jtwig> = AttributeKey("jtwig")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Jtwig {
            val config = Configuration().apply(configure)
            val feature = Jtwig(config)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is JtwigContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }
            return feature
        }
    }

    private fun process(content: JtwigContent): JtwigOutgoingContent {
        return JtwigOutgoingContent(
            createTemplate(content.template),
            content.model,
            content.etag,
            content.contentType
        )
    }

    private fun createTemplate(template: String): JtwigTemplate {
        val resourceReference = createResourceReference(template)
        return JtwigTemplate(environment, resourceReference)
    }

    private fun createResourceReference(template: String): ResourceReference {
        val templateWithPrefixAndSuffix = config.templatePrefix + template + config.templateSuffix
        return environment.resourceEnvironment
            .resourceReferenceExtractor.extract(templateWithPrefixAndSuffix)
    }
}

private class JtwigOutgoingContent(
    val template: JtwigTemplate,
    val model: Map<String, Any>,
    etag: String?,
    override val contentType: ContentType
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        val jtwigModel = JtwigModel.newModel(model)
        val outputStream = channel.toOutputStream()
        template.render(jtwigModel, outputStream)
    }

    init {
        if (etag != null)
            versions += EntityTagVersion(etag)
    }
}
