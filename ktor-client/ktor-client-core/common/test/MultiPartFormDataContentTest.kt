/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.request.forms.*
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.*
import kotlinx.io.files.Path
import kotlin.random.Random
import kotlin.test.*

class MultiPartFormDataContentTest {

    @Test
    fun testMultiPartFormDataContentHasCorrectPrefix() = runTest {
        val formData = MultiPartFormDataContent(
            formData {
                append("Hello", "World")
            }
        )

        val channel = ByteChannel()
        formData.writeTo(channel)
        channel.close()

        val actual = channel.readRemaining().readByteArray()

        assertNotEquals('\r'.code.toByte(), actual[0])
        assertNotEquals('\n'.code.toByte(), actual[1])
        assertNotEquals('\r'.code.toByte(), actual[2])
        assertNotEquals('\n'.code.toByte(), actual[3])
    }

    @Test
    fun testEmptyByteReadChannel() = runTest {
        val data = MultiPartFormDataContent(
            formData {
                append("channel", ChannelProvider { ByteReadChannel.Empty })
            },
            boundary = "boundary"
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=channel",
                "",
                "",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testByteReadChannelWithString() = runTest {
        val content = "body"
        val data = MultiPartFormDataContent(
            formData {
                append("channel", ChannelProvider(size = content.length.toLong()) { ByteReadChannel(content) })
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=channel",
                "Content-Length: 4",
                "",
                "body",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testNumberQuoted() = runTest {
        val data = MultiPartFormDataContent(
            formData {
                append("not_a_forty_two", 1337)
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=not_a_forty_two",
                "Content-Length: 4",
                "",
                "1337", // note quotes
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testBooleanQuoted() = runTest {
        val data = MultiPartFormDataContent(
            formData {
                append("is_forty_two", false)
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=is_forty_two",
                "Content-Length: 5",
                "",
                "false", // note quotes
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testStringsList() = runTest {
        val data = MultiPartFormDataContent(
            formData {
                append("platforms[]", listOf("windows", "linux", "osx"))
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 7",
                "",
                "windows",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 5",
                "",
                "linux",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 3",
                "",
                "osx",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testStringsArray() = runTest {
        val data = MultiPartFormDataContent(
            formData {
                append("platforms[]", arrayOf("windows", "linux", "osx"))
            },
            boundary = "boundary",
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 7",
                "",
                "windows",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 5",
                "",
                "linux",
                "--boundary",
                "Content-Disposition: form-data; name=\"platforms[]\"",
                "Content-Length: 3",
                "",
                "osx",
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testStringsListBadKey() = runTest {
        val attempt = {
            MultiPartFormDataContent(
                formData {
                    append("bad_key", listOf("whatever", "values"))
                },
                boundary = "boundary",
            )
        }

        val ex = assertFails { attempt() }
        assertEquals(ex.message, "Array parameter must be suffixed with square brackets ie `bad_key[]`")
    }

    @Test
    fun testByteReadChannelOverBufferSize() = runTest {
        val body = ByteArray(4089) { 'k'.code.toByte() }
        val data = MultiPartFormDataContent(
            formData {
                append("channel", ChannelProvider { ByteReadChannel(body) })
            },
            boundary = "boundary"
        )

        assertEquals(
            listOf(
                "--boundary",
                "Content-Disposition: form-data; name=channel",
                "",
                "k".repeat(4089),
                "--boundary--",
                ""
            ).joinToString(separator = "\r\n"),
            data.readString()
        )
    }

    @Test
    fun testFileContentFromSource() = runTest {
        val expected = "This content should appear in the multipart body."
        val fileSource = try {
            with(kotlinx.io.files.SystemFileSystem) {
                val file = Path(kotlinx.io.files.SystemTemporaryDirectory, "temp${Random.nextInt(1000, 9999)}.txt")
                sink(file).buffered().use { it.writeString(expected) }
                source(file).buffered()
            }
        } catch (_: Throwable) {
            // filesystem is not supported for web platforms (yet)
            return@runTest
        }
        val data = MultiPartFormDataContent(
            formData {
                append(
                    key = "key",
                    value = fileSource,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, "text/plain")
                        append(HttpHeaders.ContentDisposition, "filename=\"file.txt\"")
                    },
                )
            }
        )
        assertTrue("File contents should be present in the multipart body.") {
            data.readString().contains(expected)
        }
    }

    private suspend fun MultiPartFormDataContent.readString(charset: Charset = Charsets.UTF_8): String {
        val bytes = readBytes()
        return bytes.decodeToString(0, 0 + bytes.size)
    }

    private suspend fun MultiPartFormDataContent.readBytes(): ByteArray = coroutineScope {
        val channel = ByteChannel()
        val writeJob = launch {
            writeTo(channel)
            channel.close()
        }

        val result = channel.readRemaining().readByteArray()
        writeJob.join()

        result
    }
}
