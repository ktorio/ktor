/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.htmx.html

import io.ktor.htmx.*
import io.ktor.utils.io.*
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import kotlin.test.Test
import kotlin.test.assertEquals

class HxAttributesTest {

    @OptIn(ExperimentalKtorApi::class)
    @Test
    fun htmxAttributes() {
        val actual = buildString {
            appendHTML(prettyPrint = true).html {
                body {
                    button {
                        attributes.hx {
                            get = "/?page=1"
                            target = "#replaceMe"
                            swap = HxSwap.outerHtml
                            trigger = "click[console.log('Hello!')||true]"
                        }
                    }
                }
            }
        }
        assertEquals(
            """
            <html>
              <body><button hx-get="/?page=1" hx-target="#replaceMe" hx-swap="outerHTML" hx-trigger="click[console.log('Hello!')||true]"></button></body>
            </html>
            """.trimIndent(),
            actual.trim()
        )
    }
}
