package io.ktor.tests.server.http

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.Test
import java.io.*
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
    fun testMultiPartsPlainItemBinary() {
        val bytes = ByteArray(256) { it.toByte() }
        testMultiPartsFileItemBase(
            filename = "file.bin",
            streamProvider = { bytes.inputStream() },
            extraFileAssertions = { file -> assertEquals(hex(bytes), hex(file.streamProvider().readBytes())) }
        )
    }

    @Test
    fun testMultiPartsFileItemText() {
        val string = "file content with unicode 🌀 : здороваться : 여보세요 : 你好 : ñç"
        testMultiPartsFileItemBase(
            filename = "file.txt",
            streamProvider = { string.toByteArray().inputStream() },
            extraFileAssertions = { file -> assertEquals(string, file.streamProvider().reader().readText()) }
        )
    }

    @Test
    fun testMultiPartsFileItem() {
        val bytes = ByteArray(256) { it.toByte() }

        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            if (it != null) {
                val parts = it.readAllParts()

                assertEquals(1, parts.size)
                val file = parts[0] as PartData.FileItem

                assertEquals("fileField", file.name)
                assertEquals("file.bin", file.originalFileName)
                assertEquals(hex(bytes), hex(file.streamProvider().readBytes()))

                file.dispose()
            }
        }, setup = {
            addHeader(HttpHeaders.ContentType, contentType.toString())
            setBody(boundary, listOf(PartData.FileItem(
                    streamProvider = { bytes.inputStream() },
                    dispose = {},
                    partHeaders = headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File
                                    .withParameter(ContentDisposition.Parameters.Name, "fileField")
                                    .withParameter(ContentDisposition.Parameters.FileName, "file.bin")
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

    private fun testMultiPartsFileItemBase(
        filename: String,
        streamProvider: () -> InputStream,
        extraFileAssertions: (file: PartData.FileItem) -> Unit
    ) {
        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            if (it != null) {
                val parts = it.readAllParts()

                assertEquals(1, parts.size)
                val file = parts[0] as PartData.FileItem

                assertEquals("fileField", file.name)
                assertEquals(filename, file.originalFileName)
                extraFileAssertions(file)

                file.dispose()
            }
        }, setup = {
            addHeader(HttpHeaders.ContentType, contentType.toString())
            setBody(boundary, listOf(PartData.FileItem(
                streamProvider = { streamProvider() },
                dispose = {},
                partHeaders = headersOf(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.File
                        .withParameter(ContentDisposition.Parameters.Name, "fileField")
                        .withParameter(ContentDisposition.Parameters.FileName, filename)
                        .toString()
                )
            )))
        })
    }
}