/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import org.junit.runner.*
import org.junit.runners.*
import java.io.*
import java.nio.file.*
import java.text.*
import java.time.*
import java.util.*
import kotlin.test.*

@RunWith(Parameterized::class)
class LastModifiedTest(@Suppress("UNUSED_PARAMETER") name: String, zone: ZoneId) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun zones(): List<Array<Any>> =
            listOf(arrayOf("GMT", ZoneId.of("GMT")), arrayOf("SomeLocal", ZoneId.of("GMT+1")))
    }

    private val date = ZonedDateTime.now(zone)!!

    private fun withConditionalApplication(body: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ConditionalHeaders) {
                version { listOf(LastModifiedVersion(date)) }
            }
            routing {
                handle {
                    call.respondText("response")
                }
            }
        }

        runBlocking {
            body()
        }
    }

    @Test
    fun testNoHeaders(): Unit = withConditionalApplication {
        client.get("/").let { result ->
            assertEquals(HttpStatusCode.OK, result.status)
            assertEquals("response", result.bodyAsText())
        }
    }

    @Test
    fun testSubrouteInstall(): Unit = testApplication {
        application {
            routing {
                route("1") {
                    install(ConditionalHeaders) {
                        version { listOf(LastModifiedVersion(date)) }
                    }
                    get { call.respond("response") }
                }
                get("2") { call.respond("response") }
            }
        }
        client.get("/1") {
            header(HttpHeaders.IfModifiedSince, date.toHttpDateString())
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }

        client.get("/2") {
            header(HttpHeaders.IfModifiedSince, date.toHttpDateString())
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @Test
    fun testIfModifiedSinceEq(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @Test
    fun testIfModifiedSinceEqZoned() = testApplication {
        application {
            install(ConditionalHeaders) {
                version { listOf(LastModifiedVersion(date)) }
            }
            routing {
                handle {
                    call.respondText("response")
                }
            }
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @Test
    fun testIfModifiedSinceLess(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.minusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @Test
    fun testIfModifiedSinceGt(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @Test
    fun testIfUnModifiedSinceEq(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @Test
    fun testIfUnModifiedSinceLess(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.minusDays(1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @Test
    fun testIfUnModifiedSinceGt(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }
    }

    @Test
    fun testIfUnmodifiedSinceIllegal(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                "zzz"
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @Test
    fun testIfUnmodifiedSinceMultiple(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                ""
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @Test
    fun testIfModifiedSinceMultiple(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                ""
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(2).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }
    }

    @Test
    fun testBoth(): Unit = withConditionalApplication {
        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                ""
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                ""
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("response", response.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.PreconditionFailed, it.status)
            assertEquals("", it.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            assertEquals("", it.bodyAsText())
        }

        client.get("/") {
            header(
                HttpHeaders.IfModifiedSince,
                date.plusDays(1).toHttpDateString()
            )
            header(
                HttpHeaders.IfUnmodifiedSince,
                date.plusDays(-1).toHttpDateString()
            )
        }.let {
            assertEquals(HttpStatusCode.NotModified, it.status)
            // both conditions are not met but actually it is not clear which one should win
            // so we declare the order
            assertEquals("", it.bodyAsText())
        }
    }
}

class LastModifiedVersionTest {
    private fun temporaryDefaultTimezone(timeZone: TimeZone, block: () -> Unit) {
        val originalTimeZone: TimeZone = TimeZone.getDefault()
        TimeZone.setDefault(timeZone)
        try {
            block()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun checkLastModifiedHeaderIsIndependentOfLocalTimezone(
        constructLastModifiedVersion: (Date) -> LastModifiedVersion
    ) {
        // setup: any non-zero-offset-Timezone will do
        temporaryDefaultTimezone(TimeZone.getTimeZone("GMT+08:00")) {
            // guard: local default timezone needs to be different from GMT for the problem to manifest
            assertTrue(
                TimeZone.getDefault().rawOffset != 0,
                "invalid test setup - local timezone is GMT: ${TimeZone.getDefault()}"
            )

            // setup: last modified for file
            val expectedLastModified: Date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").parse("2018-03-04 15:12:23 GMT")

            // setup: object to test
            val lastModifiedVersion = constructLastModifiedVersion(expectedLastModified)

            // setup HeadersBuilder as test spy
            val headersBuilder = HeadersBuilder()

            // exercise
            lastModifiedVersion.appendHeadersTo(headersBuilder)

            // check
            assertEquals("Sun, 04 Mar 2018 15:12:23 GMT", headersBuilder["Last-Modified"])
        }
    }

    @Test
    fun lastModifiedHeaderFromDateIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            LastModifiedVersion(input)
        }
    }

    @Test
    fun lastModifiedHeaderFromLocalDateTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            LastModifiedVersion(ZonedDateTime.ofInstant(input.toInstant(), ZoneId.systemDefault()))
        }
    }

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun lastModifiedHeaderFromFileTimeIsIndependentOfLocalTimezone() {
        checkLastModifiedHeaderIsIndependentOfLocalTimezone { input: Date ->
            // setup: create file
            val file: File = temporaryFolder.newFile("foo.txt").apply {
                setLastModified(input.time)
            }

            // guard: file lastmodified is actually set as expected
            assertEquals(input.time, file.lastModified())

            // setup: object to test
            LastModifiedVersion(Files.getLastModifiedTime(file.toPath()))
        }
    }
}
