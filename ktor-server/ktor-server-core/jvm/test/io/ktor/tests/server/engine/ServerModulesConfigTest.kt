/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.engine

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class ServerModulesConfigTest {

    @Test
    fun `function reference`() =
        testModule("module1")

    @Test
    fun `lambda property reference`() =
        testModule("module2")

    @Test
    fun `method reference`() =
        testModule("module3")

    private fun testModule(moduleName: String) = testApplication {
        environment {
            config = HoconConfigLoader().load("modules/application-$moduleName.conf")!!
        }
        client.get("/$moduleName").apply {
            assertEquals("OK", bodyAsText())
        }
    }

}

fun HttpServer.module1() {
    routing {
        get("/module1") { call.respondText("OK") }
    }
}

val module2: HttpServer.() -> Unit = {
    routing {
        get("/module2") { call.respondText("OK") }
    }
}

class ModuleClass {
    fun HttpServer.module3() {
        routing {
            get("/module3") { call.respondText("OK") }
        }
    }
}
