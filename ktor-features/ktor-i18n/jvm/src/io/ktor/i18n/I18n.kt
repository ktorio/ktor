/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.i18n

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.nio.charset.*

/**
 * Represents I18n feature and its configuration.
 *
 * install(I18n) {
 *    defaultLanguage = "pt-BR"
 *    encoding = StandardCharsets.UTF_8
 * }
 */
class I18n(configuration: Configuration) {

    private val availableLanguages = configuration.availableLanguages

    private val defaultLanguage = configuration.defaultLanguage

    /**
     * I18n configuration. Currently supports [encoding] and [defaultLanguage]
     *
     * [defaultLanguage] must follow IETF BCP 47 language tag string specification
     */
    class Configuration {
        var availableLanguages: List<String> = emptyList()
        var defaultLanguage: String = ""
    }

    private fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val acceptedLanguages = context.call.request.acceptLanguageItems()
        context.call.attributes.put(acceptedLanguagesKey, acceptedLanguages)
        context.call.attributes.put(availableLanguagesKey, availableLanguages)
        context.call.attributes.put(defaultLanguageKey, defaultLanguage)
    }

    companion object Feature : ApplicationFeature<Application, Configuration, I18n> {
        val acceptedLanguagesKey = AttributeKey<List<HeaderValue>>("AcceptedLanguages")
        val availableLanguagesKey = AttributeKey<List<String>>("AvailableLanguages")
        val defaultLanguageKey = AttributeKey<String>("DefaultLanguage")

        override val key = AttributeKey<I18n>("I18n")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): I18n {
            val configuration = Configuration().apply(configure)
            val feature = I18n(configuration)

            pipeline.intercept(ApplicationCallPipeline.Call) {
                feature.intercept(this)
            }

            return feature
        }
    }
}
