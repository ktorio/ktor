/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.i18n

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*
import io.ktor.i18n.*
import io.ktor.response.*

class TranslationTest {

    private val testAvailableLanguages = listOf(
        "en-US", "pt-BR", "ru-RU"
    )

    @Test
    fun testTranslationToLanguageSpecifiedInAcceptLanguageHeader() = withTestApplication {
        application.install(I18n) {
            availableLanguages = testAvailableLanguages
        }

        application.routing {
            get("/") {
                val valueInPortuguese = i18n("some_key")
                call.respond(OK, valueInPortuguese)
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.AcceptLanguage, "pt-BR")
        }.response.let { response ->
            assertEquals(OK, response.status())
            assertNotNull(response.content)

            val contentAsString = response.content!!
            assertEquals("Portuguese Key", contentAsString)
        }
    }

    @Test
    fun testTranslationToDefaultLanguage() = withTestApplication {
        application.install(I18n) {
            availableLanguages = testAvailableLanguages
            defaultLanguage = "en-US"
        }

        application.routing {
            get("/") {
                val valueInPortuguese = i18n("some_key")
                call.respond(OK, valueInPortuguese)
            }
        }

        handleRequest(HttpMethod.Get, "/").response.let { response ->
            assertEquals(OK, response.status())
            assertNotNull(response.content)

            val contentAsString = response.content!!
            assertEquals("English Key", contentAsString)
        }
    }

    @Test
    fun testDefaultBundleWhenDefaultLanguageIsNotConfigured() = withTestApplication {
        application.install(I18n) {
            availableLanguages = testAvailableLanguages
        }

        application.routing {
            get("/") {
                val valueInPortuguese = i18n("some_key")
                call.respond(OK, valueInPortuguese)
            }
        }

        handleRequest(HttpMethod.Get, "/").response.let { response ->
            assertEquals(OK, response.status())
            assertNotNull(response.content)

            val contentAsString = response.content!!
            assertEquals("Default key", contentAsString)
        }
    }

    @Test
    fun testTranslationToPreferredLanguage() = withTestApplication {
        application.install(I18n) {
            availableLanguages = testAvailableLanguages
        }

        application.routing {
            get("/") {
                val valueInPortuguese = i18n("some_key")
                call.respond(OK, valueInPortuguese)
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.AcceptLanguage, "ru-RU, pt-BR;q=0.8, en-US;q=0.5, da;q=0.3")
        }.response.let { response ->
            assertEquals(OK, response.status())
            assertNotNull(response.content)

            val contentAsString = response.content!!
            assertEquals("русский ключ", contentAsString)
        }
    }

}

