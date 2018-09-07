package io.ktor.tests.server.http

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.io.core.*
import kotlinx.io.streams.*
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
    fun testMultiPartsPlainItemBinary() {
        val bytes = ByteArray(256) { it.toByte() }
        testMultiPartsFileItemBase(
            filename = "file.bin",
            provider = { buildPacket { writeFully(bytes) } },
            extraFileAssertions = { file -> assertEquals(hex(bytes), hex(file.provider().readBytes())) }
        )
    }

    @Test
    fun testMultiPartsFileItemText() {
        val string = "file content with unicode 🌀 : здороваться : 여보세요 : 你好 : ñç"
        testMultiPartsFileItemBase(
            filename = "file.txt",
            provider = { buildPacket { writeFully(string.toByteArray()) } },
            extraFileAssertions = { file -> assertEquals(string, file.provider().readText()) }
        )
    }

    @Test
    fun testMultiPartsFileItem() {
        val bytes = ByteArray(256) { it.toByte() }

        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            val parts = it.readAllParts()

            assertEquals(1, parts.size)
            val file = parts[0] as PartData.FileItem

            assertEquals("fileField", file.name)
            assertEquals("file.bin", file.originalFileName)
            assertEquals(hex(bytes), hex(file.provider().readBytes()))

            file.dispose()
        }, setup = {
            addHeader(HttpHeaders.ContentType, contentType.toString())
            setBody(boundary, listOf(
                PartData.FileItem(
                    provider = { bytes.inputStream().asInput() },
                    dispose = {},
                    partHeaders = headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File
                                .withParameter(ContentDisposition.Parameters.Name, "fileField")
                                .withParameter(ContentDisposition.Parameters.FileName, "file.bin")
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
        provider: () -> Input,
        extraFileAssertions: (file: PartData.FileItem) -> Unit
    ) {
        testMultiParts({
            assertNotNull(it, "it should be multipart data")
            val parts = it.readAllParts()

            assertEquals(1, parts.size)
            val file = parts[0] as PartData.FileItem

            assertEquals("fileField", file.name)
            assertEquals(filename, file.originalFileName)
            extraFileAssertions(file)

            file.dispose()
        }, setup = {
            addHeader(HttpHeaders.ContentType, contentType.toString())
            setBody(boundary, listOf(
                PartData.FileItem(
                provider = provider,
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
