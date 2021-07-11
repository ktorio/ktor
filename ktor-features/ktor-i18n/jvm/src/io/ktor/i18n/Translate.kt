/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.i18n

import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.charsets.*
import java.util.*

/**
 * Translate a message key to an accepted language specified in HTTP request
 */

fun PipelineContext<Unit, ApplicationCall>.i18n(key: String): String {
    val acceptedLanguages = context.attributes[I18n.acceptedLanguagesKey]
    val availableLanguages = context.attributes[I18n.availableLanguagesKey]
    val defaultLanguage = context.attributes[I18n.defaultLanguageKey]

    val bestMatchLanguage = acceptedLanguages.firstOrNull {
        availableLanguages.contains(it.value)
    }?.value ?: defaultLanguage

    val locale = Locale.forLanguageTag(bestMatchLanguage)

    val bundle = ResourceBundle.getBundle("messages/messages", locale)
    val value = bundle.getEncodedString(key)

    application.log.debug("translating to $locale - acceptedLanguages=${acceptedLanguages}: $key=$value")
    return value
}

/**
 * By default, .properties file in Java has ISO-8859-1 as encoding and this extension is responsible
 * for converting to UTF-8, so we can use Unicode in any properties values.
 *
 */

fun ResourceBundle.getEncodedString(key: String): String {
    val value = this.getString(key).toByteArray(Charset.forName("ISO-8859-1"))
    return String(value, Charset.forName("UTF-8"))
}
