package io.ktor.tests.http

import io.ktor.http.*
import org.junit.Test
import kotlin.test.*

class ContentTypeLookupTest {
    private val plainText = listOf(ContentType.Text.Plain)

    @Test
    fun testExtensionSingle() {
        assertEquals(plainText, ContentType.fromFileExtension(".txt"))
    }

    @Test
    fun testExtensionMultiple() {
        assertEquals(listOf(ContentType.parse("audio/x-pn-realaudio-plugin"), ContentType.parse("application/x-rpm")), ContentType.fromFileExtension(".rpm"))
    }

    @Test
    fun testMissing() {
        assertEquals(emptyList(), ContentType.fromFileExtension(".werfewrfgewrf"))
    }

    @Test
    fun testEmpty() {
        assertEquals(emptyList(), ContentType.fromFileExtension(""))
        assertEquals(emptyList(), ContentType.fromFileExtension(""))
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