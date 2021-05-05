import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.test.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

class FormDslTest {

    @Test
    fun testAppendDoesNotEscapeKeyAndFilenameIfNotNeeded() {
        val data = formData {
            append(
                key = "file",
                filename = "file.name"
            ) {}
        }
        assertEquals(data.first().headers.getAll(HttpHeaders.ContentDisposition)!![0], "form-data; name=file")
        assertEquals(data.first().headers.getAll(HttpHeaders.ContentDisposition)!![1], "filename=file.name")
    }

    @Test
    fun testAppendEscapeKeyAndFilenameIfNeeded() {
        val data = formData {
            append(
                key = "file 1",
                filename = "file 1.name"
            ) {}
        }
        assertEquals(data.first().headers.getAll(HttpHeaders.ContentDisposition)!![0], "form-data; name=\"file 1\"")
        assertEquals(data.first().headers.getAll(HttpHeaders.ContentDisposition)!![1], "filename=\"file 1.name\"")
    }

    @Test
    fun testAppendDoesNotAddDoubleQuotes() {
        val data = formData {
            append(
                key = "\"file 1\"",
                filename = "\"file 1.name\""
            ) {}
        }
        assertEquals("form-data; name=\"file 1\"", data.first().headers.getAll(HttpHeaders.ContentDisposition)!![0])
        assertEquals("filename=\"file 1.name\"", data.first().headers.getAll(HttpHeaders.ContentDisposition)!![1])
    }
}
