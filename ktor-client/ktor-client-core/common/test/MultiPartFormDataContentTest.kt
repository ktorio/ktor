import io.ktor.client.request.forms.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

class MultiPartFormDataContentTest {

    @Test
    fun testMultiPartFormDataContentHasCorrectPrefix() = testSuspend {
        val formData = MultiPartFormDataContent(
            formData {
                append("Hello", "World")
            }
        )

        val channel = ByteChannel()
        formData.writeTo(channel)
        channel.close()

        val actual = channel.readRemaining().readBytes()

        assertNotEquals('\r'.code.toByte(), actual[0])
        assertNotEquals('\n'.code.toByte(), actual[1])
        assertNotEquals('\r'.code.toByte(), actual[2])
        assertNotEquals('\n'.code.toByte(), actual[3])
    }

    @Test
    fun testEmptyByteReadChannel() = testSuspend {
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
    fun testByteReadChannelWithString() = testSuspend {
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
    fun testByteReadChannelOverBufferSize() = testSuspend {
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

    private suspend fun MultiPartFormDataContent.readString(charset: Charset = Charsets.UTF_8): String {
        return String(readBytes(), charset = charset)
    }

    private suspend fun MultiPartFormDataContent.readBytes(): ByteArray = coroutineScope {
        val channel = ByteChannel()
        val writeJob = launch {
            writeTo(channel)
            channel.close()
        }

        val result = channel.readRemaining().readBytes()
        writeJob.join()

        result
    }
}
