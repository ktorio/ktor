/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.calllogging

import io.ktor.client.request.get
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.fusesource.jansi.AnsiPrintStream
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.Test
import kotlin.test.assertIsNot

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TerminalTest {

    @Order(0)
    @Test
    fun triggerAnsiInstall() = testApplication {
        application {
            install(CallLogging)
            routing {
                get("/") { call.respondText("ok") }
            }
        }
        client.get("/")
    }

    @Order(1)
    @Test
    fun verifyOutAndErrStreams() {
        assertIsNot<AnsiPrintStream>(System.out)
        assertIsNot<AnsiPrintStream>(System.err)
        println("Test passes") // To observe System.out
    }
}
