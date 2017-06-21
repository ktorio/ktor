package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.test.*

class TestHostMultipartTest {

    @Test
    fun testNonMultipart() {
        testMultiParts({
            assertNull(it, "it should be no multipart data")
        }, setup = {})
    }

    @Test
    fun testMultiPartsDefault() {
        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            if (it != null) {
                assertEquals(emptyList<PartData>(), it.parts.toList())
            }
        }, setup = {
            addHeader(HttpHeaders.ContentType, ContentType.MultiPart.FormData.toString())
        })
    }

    @Test
    fun testMultiPartsPlainItem() {
        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            if (it != null) {
                val parts = it.parts.toList()

                assertEquals(1, parts.size)
                assertEquals("field1", parts[0].partName)
                assertEquals("plain field", (parts[0] as PartData.FormItem).value)
                parts[0].dispose()
            }
        }, setup = {
            addHeader(HttpHeaders.ContentType, ContentType.MultiPart.FormData.toString())
            multiPartEntries = listOf(
                    PartData.FormItem(
                            "plain field",
                            dispose = {},
                            partHeaders = valuesOf(
                                    HttpHeaders.ContentDisposition to listOf(ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "field1").toString())
                            )
                    )
            )
        })
    }

    @Test
    fun testMultiPartsFileItem() {
        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            if (it != null) {
                val parts = it.parts.toList()

                assertEquals(1, parts.size)
                val file = parts[0] as PartData.FileItem

                assertEquals("fileField", file.partName)
                assertEquals("file.txt", file.originalFileName)
                assertEquals("file content", file.streamProvider().reader().readText())

                file.dispose()
            }
        }, setup = {
            addHeader(HttpHeaders.ContentType, ContentType.MultiPart.FormData.toString())
            multiPartEntries = listOf(
                    PartData.FileItem(
                            streamProvider = { "file content".toByteArray().inputStream() },
                            dispose = {},
                            partHeaders = valuesOf(
                                    HttpHeaders.ContentDisposition to listOf(
                                            ContentDisposition.File
                                                    .withParameter(ContentDisposition.Parameters.Name, "fileField")
                                                    .withParameter(ContentDisposition.Parameters.FileName, "file.txt")
                                                    .toString()
                                    )
                            )
                    )
            )
        })
    }

    @Test
    fun testMultiPartShouldFail() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                try {
                    call.request.receive<MultiPartData>().parts.toList()
                } catch (error: Throwable) {
                    fail("This pipeline shouldn't finish successfully")
                }
            }

            assertFailsWith<AssertionError> {
                handleRequest(HttpMethod.Post, "/")
            }
        }
    }

    fun testMultiParts(asserts: (MultiPartData?) -> Unit, setup: TestApplicationRequest.() -> Unit) {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                if (call.request.isMultipart()) {
                    asserts(call.request.receive<MultiPartData>())
                } else {
                    asserts(null)
                }
            }

            handleRequest(HttpMethod.Post, "/", setup)
        }
    }
}