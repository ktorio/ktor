package io.ktor.tests.server.http

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class TestEngineMultipartTest {
    private val boundary = "***bbb***"
    private val contentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)

    @Test
    fun testNonMultipart() {
        testMultiParts({
            assertNull(it, "it should be no multipart data")
        }, setup = {})
    }

    @Test
    fun testMultiPartsPlainItem() {
        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            if (it != null) {
                val parts = it.readAllParts()

                assertEquals(1, parts.size)
                assertEquals("field1", parts[0].name)
                assertEquals("plain field", (parts[0] as PartData.FormItem).value)
                parts[0].dispose()
            }
        }, setup = {
            addHeader(HttpHeaders.ContentType, contentType.toString())
            setBody(boundary, listOf(PartData.FormItem(
                    "plain field",
                    dispose = {},
                    partHeaders = headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "field1").toString()
                    )
            )))
        })
    }

    @Test
    fun testMultiPartsFileItem() {
        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            if (it != null) {
                val parts = it.readAllParts()

                assertEquals(1, parts.size)
                val file = parts[0] as PartData.FileItem

                assertEquals("fileField", file.name)
                assertEquals("file.txt", file.originalFileName)
                assertEquals("file content", file.streamProvider().reader().readText())

                file.dispose()
            }
        }, setup = {
            addHeader(HttpHeaders.ContentType, contentType.toString())
            setBody(boundary, listOf(PartData.FileItem(
                    streamProvider = { "file content".toByteArray().inputStream() },
                    dispose = {},
                    partHeaders = headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File
                                    .withParameter(ContentDisposition.Parameters.Name, "fileField")
                                    .withParameter(ContentDisposition.Parameters.FileName, "file.txt")
                                    .toString()
                    )
            )))
        })
    }

    @Test
    fun testMultiPartShouldFail() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                try {
                    call.receiveMultipart().readAllParts()
                } catch (error: Throwable) {
                    fail("This pipeline shouldn't finish successfully")
                }
            }

            assertFailsWith<AssertionError> {
                handleRequest(HttpMethod.Post, "/")
            }
        }
    }

    private fun testMultiParts(asserts: suspend (MultiPartData?) -> Unit, setup: TestApplicationRequest.() -> Unit) {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                if (call.request.isMultipart()) {
                    asserts(call.receiveMultipart())
                } else {
                    asserts(null)
                }
            }

            handleRequest(HttpMethod.Post, "/", setup)
        }
    }
}