/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request.forms

import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.test.*

class FormDslJsTest {

    @Test
    fun `appendBlob creates form part from Blob`() = runTest {
        val blob = Blob(arrayOf("hello world"), BlobPropertyBag("text/plain"))

        val parts = formData {
            appendBlob("file", blob)
        }

        val data = MultiPartFormDataContent(parts, boundary = "boundary")
        val result = data.readString()

        assertTrue(result.contains("Content-Disposition: form-data; name=\"file\""))
        assertTrue(result.contains("hello world"))
    }

    @Test
    fun `appendBlob with filename and content type`() = runTest {
        val blob = Blob(arrayOf("file content"), BlobPropertyBag("application/octet-stream"))

        val parts = formData {
            appendBlob("upload", blob, "test.txt", ContentType.Text.Plain)
        }

        val data = MultiPartFormDataContent(parts, boundary = "boundary")
        val result = data.readString()

        assertTrue(result.contains("Content-Disposition: form-data; name=\"upload\""))
        assertTrue(result.contains("filename=\"test.txt\""))
        assertTrue(result.contains("Content-Type: text/plain"))
        assertTrue(result.contains("file content"))
    }

    @Test
    fun `appendBlob preserves size in Content-Length`() = runTest {
        val content = "test content"
        val blob = Blob(arrayOf(content), BlobPropertyBag("text/plain"))

        val parts = formData {
            appendBlob("data", blob)
        }

        val data = MultiPartFormDataContent(parts, boundary = "boundary")
        val result = data.readString()

        assertTrue(result.contains("Content-Length: ${content.encodeToByteArray().size}"))
    }

    private suspend fun MultiPartFormDataContent.readString(): String {
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
