package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.junit.*
import kotlin.test.*

class ContentTypeLookupTest {
    @Test
    fun testExtensionSingle() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFileExtension(".txt"))
    }

    @Test
    fun testExtensionMultiple() {
        assertEquals(listOf(ContentType.parse("audio/x-pn-realaudio-plugin"), ContentType.parse("application/x-rpm")), ContentType.fromFileExtension(".rpm"))
    }

    @Test
    fun testMissing() {
        assertEquals(emptyList<ContentType>(), ContentType.fromFileExtension(".werfewrfgewrf"))
    }

    @Test
    fun testEmpty() {
        assertEquals(emptyList<ContentType>(), ContentType.fromFileExtension("."))
        assertEquals(emptyList<ContentType>(), ContentType.fromFileExtension(""))
    }

    @Test
    fun testWrongCharacterCase() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFileExtension(".Txt"))
    }

    @Test
    fun testMissingDot() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFileExtension("txt"))
    }

    @Test
    fun testByPathNoPath() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFilePath("aa.txt"))
    }

    @Test
    fun testByPathNoExtNoReg() {
        assertEquals(emptyList<ContentType>(), ContentType.fromFilePath("aa"))
    }

    @Test
    fun testByPathNoExt() {
        assertEquals(emptyList<ContentType>(), ContentType.fromFilePath("txt"))
    }

    @Test
    fun testByPathWithPath() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFilePath("/path/to/file/aa.txt"))
    }

    @Test
    fun testByPathWithPathWindows() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFilePath("C:\\path\\to\\file\\aa.txt"))
    }

    @Test
    fun testByPathWithPathStartsWithDot() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFilePath("/path/to/file/.txt"))
    }

    @Test
    fun testByPathNoPathStartsWithDot() {
        assertEquals(listOf(ContentType.Text.Plain), ContentType.fromFilePath(".txt"))
    }

    @Test
    fun testLookupExtensionByContentType() {
        assertEquals(listOf("djvu"), ContentType.parse("image/vnd.djvu").fileExtensions())
    }
}