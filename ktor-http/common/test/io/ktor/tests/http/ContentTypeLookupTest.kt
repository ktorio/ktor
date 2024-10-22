/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class ContentTypeLookupTest {
    private val plainText = listOf(ContentType.Text.Plain)

    @Test
    fun testExtensionSingle() {
        assertEquals(plainText, ContentType.fromFileExtension(".txt"))
    }

    @Test
    fun testExtensionMultiple() {
        assertEquals(
            listOf(
                ContentType.parse("audio/x-pn-realaudio-plugin"),
                ContentType.parse("application/x-rpm")
            ),
            ContentType.fromFileExtension(".rpm")
        )

        assertEquals(
            listOf(
                ContentType.parse("application/macbinary"),
                ContentType.parse("application/mac-binary"),
                ContentType.parse("application/octet-stream"),
                ContentType.parse("application/x-binary"),
                ContentType.parse("application/x-macbinary")
            ),
            ContentType.fromFileExtension(".bin")
        )

        assertEquals(
            listOf(
                ContentType.parse("application/zip"),
                ContentType.parse("application/x-compressed"),
                ContentType.parse("application/x-zip-compressed"),
                ContentType.parse("multipart/x-zip")
            ),
            ContentType.fromFileExtension(".zip")
        )

        assertEquals(
            listOf(
                ContentType.parse("video/mpeg"),
                ContentType.parse("audio/mpeg")
            ),
            ContentType.fromFileExtension(".mpg")
        )

        assertEquals(
            listOf(
                ContentType.parse("video/mp4"),
                ContentType.parse("application/mp4")
            ),
            ContentType.fromFileExtension(".mp4")
        )

        assertEquals(
            listOf(
                ContentType.parse("video/x-matroska"),
                ContentType.parse("audio/x-matroska"),
            ),
            ContentType.fromFileExtension(".mkv")
        )

        assertEquals(
            listOf(
                ContentType.parse("text/javascript"),
                ContentType.parse("application/javascript"),
            ),
            ContentType.fromFileExtension(".js")
        )
    }

    @Test
    fun testMissing() {
        assertEquals(emptyList(), ContentType.fromFileExtension(".werfewrfgewrf"))
    }

    @Test
    fun testEmpty() {
        assertEquals(emptyList(), ContentType.fromFileExtension(""))
        assertEquals(emptyList(), ContentType.fromFileExtension("."))
    }

    @Test
    fun testWrongCharacterCase() {
        assertEquals(plainText, ContentType.fromFileExtension(".Txt"))
    }

    @Test
    fun testMissingDot() {
        assertEquals(plainText, ContentType.fromFileExtension("txt"))
    }

    @Test
    fun testByPathNoPath() {
        assertEquals(plainText, ContentType.fromFilePath("aa.txt"))
    }

    @Test
    fun testByPathNoExtNoReg() {
        assertEquals(emptyList(), ContentType.fromFilePath("aa"))
    }

    @Test
    fun testByPathNoExt() {
        assertEquals(emptyList(), ContentType.fromFilePath("txt"))
    }

    @Test
    fun testByPathWithPath() {
        assertEquals(plainText, ContentType.fromFilePath("/path/to/file/aa.txt"))
    }

    @Test
    fun testByPathWithPathWindows() {
        assertEquals(plainText, ContentType.fromFilePath("C:\\path\\to\\file\\aa.txt"))
    }

    @Test
    fun testByPathWithPathStartsWithDot() {
        assertEquals(plainText, ContentType.fromFilePath("/path/to/file/.txt"))
    }

    @Test
    fun testByPathNoPathStartsWithDot() {
        assertEquals(plainText, ContentType.fromFilePath(".txt"))
    }

    @Test
    fun testLookupExtensionByContentType() {
        assertEquals(listOf("djvu"), ContentType.parse("image/vnd.djvu").fileExtensions())
    }
}
