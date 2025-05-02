/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.i18n

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import org.jetbrains.annotations.PropertyKey
import java.util.Locale
import java.util.ResourceBundle

private val I18N_LOGGER = KtorSimpleLogger("io.ktor.i18n")
private val REQUIRED_RESPONSE_LANGUAGE = AttributeKey<String>("ResponseLanguage")
private val RESOURCE_BUNDLE = AttributeKey<ResourceBundle>("I18NLocale")
private val LOCALE = AttributeKey<Locale>("I18NLocale")

/**
 * I18n configuration. Currently, it supports [availableLanguages] and [defaultLanguage]
 *
 * [defaultLanguage] must follow IETF BCP 47 language tag string specification
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.i18n.I18nConfiguration)
 */
public class I18nConfiguration {
    public var availableLanguages: List<String> = listOf("en-US")
    public var defaultLanguage: String = ""
}

/**
 * Represents I18n feature and its configuration.
 *
 * install(I18n) {
 *    availableLanguages = listOf("en-US", "pt-BR")
 *    defaultLanguage = "en-US"
 * }
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.i18n.I18n)
 */
public val I18n: ApplicationPlugin<I18nConfiguration> = createApplicationPlugin(
    "I18n",
    ::I18nConfiguration
) {
    val availableLanguages = pluginConfig.availableLanguages
    val defaultLanguage = pluginConfig.defaultLanguage

    onCall {
        val acceptedLanguages = it.request.acceptLanguageItems()

        val bestMatchLanguage = acceptedLanguages.firstOrNull {
            it.value in availableLanguages
        }?.value ?: defaultLanguage

        it.attributes[REQUIRED_RESPONSE_LANGUAGE] = bestMatchLanguage

        val locale = Locale.forLanguageTag(bestMatchLanguage)
        it.attributes[LOCALE] = locale

        val bundle = ResourceBundle.getBundle(BUNDLE_KEY, locale)
        it.attributes[RESOURCE_BUNDLE] = bundle
    }

    onCallRespond { call ->
        val value = call.attributes[REQUIRED_RESPONSE_LANGUAGE]
        if (value.isNotBlank()) {
            call.response.header(HttpHeaders.ContentLanguage, value)
        }
    }
}

/**
 * Get the current accepted [locale](java.util.Locale) specified in the HTTP request
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.i18n.locale)
 */
public val ApplicationCall.locale: Locale get() = attributes[LOCALE]

/**
 * Translate a message key to an accepted language specified in HTTP request
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.i18n.i18n)
 */
public fun ApplicationCall.i18n(@PropertyKey(resourceBundle = BUNDLE_KEY) key: String): String {
    val value = attributes[RESOURCE_BUNDLE].getString(key)

    I18N_LOGGER.debug {
        "translating to $locale: $key=$value"
    }
    return value
}

internal const val BUNDLE_KEY = "messages.messages"
