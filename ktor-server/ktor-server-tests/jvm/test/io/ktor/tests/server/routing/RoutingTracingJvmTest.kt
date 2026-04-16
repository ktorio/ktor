/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.io.File
import kotlin.test.*

class RoutingTracingJvmTest {

    private val basedir = listOf(File("jvm/test"), File("ktor-server/ktor-server-tests/jvm/test"))
        .map { it.resolve("io/ktor/server") }
        .first(File::exists)

    @Test
    fun testStaticFilesRouteTracing() = testApplication {
        var trace: RoutingResolveTrace? = null
        application {
            routing {
                trace { trace = it }
                staticFiles("/static", basedir)
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/static/plugins/StaticContentTest.kt").status)

        assertEquals(
            """
            Trace for [static, plugins, StaticContentTest.kt]
            /, segment:0 -> SUCCESS @ /
              / [(static-content)], segment:0 -> SUCCESS @ / [(static-content)]
                /static [(static-content)], segment:1 -> SUCCESS @ /static [(static-content)]
                  /static/{...} [(static-content)], segment:3 -> SUCCESS; Parameters [static-content-path-parameter=[plugins, StaticContentTest.kt]] @ /static/{...} [(static-content)]
                    /static/{...} [(static-content), (method:GET)], segment:3 -> SUCCESS @ /static/{...} [(static-content), (method:GET)]
            Matched routes:
              "" -> "(static-content)" -> "static" -> "{...}" -> "(method:GET)"
            Routing resolve result:
              SUCCESS; Parameters [static-content-path-parameter=[plugins, StaticContentTest.kt]] @ /static/{...} [(static-content), (method:GET)]
            """.trimIndent(),
            trace!!.buildText()
        )
    }
}
