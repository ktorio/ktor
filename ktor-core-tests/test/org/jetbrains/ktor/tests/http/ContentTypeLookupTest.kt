package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.junit.*
import kotlin.test.*

class ContentTypeLookupTest {
    @Test
    fun testExtensionSingle() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByExtension(".txt"))
    }

    @Test
    fun testExtensionMultiple() {
        assertEquals(listOf(ContentType.parse("audio/x-pn-realaudio-plugin"), ContentType.parse("application/x-rpm")), ContentTypeByExtension.lookupByExtension(".rpm"))
    }

    @Test
    fun testMissing() {
        assertEquals(emptyList<ContentType>(), ContentTypeByExtension.lookupByExtension(".werfewrfgewrf"))
    }

    @Test
    fun testEmpty() {
        assertEquals(emptyList<ContentType>(), ContentTypeByExtension.lookupByExtension("."))
        assertEquals(emptyList<ContentType>(), ContentTypeByExtension.lookupByExtension(""))
    }

    @Test
    fun testWrongCharacterCase() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByExtension(".Txt"))
    }

    @Test
    fun testMissingDot() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByExtension("txt"))
    }

    @Test
    fun testByPathNoPath() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByPath("aa.txt"))
    }

    @Test
    fun testByPathNoExt() {
        assertEquals(emptyList<ContentType>(), ContentTypeByExtension.lookupByPath("aa"))
    }

    @Test
    fun testByPathWithPath() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByPath("/path/to/file/aa.txt"))
    }

    @Test
    fun testByPathWithPathWindows() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByPath("C:\\path\\to\\file\\aa.txt"))
    }

    @Test
    fun testByPathWithPathStartsWithDot() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByPath("/path/to/file/.txt"))
    }

    @Test
    fun testByPathNoPathStartsWithDot() {
        assertEquals(listOf(ContentType.Text.Plain), ContentTypeByExtension.lookupByPath(".txt"))
    }

    @Test
    fun testLookupExtensionByContentType() {
        assertEquals(listOf("djvu"), ContentTypeByExtension.lookupByContentType(ContentType.parse("image/vnd.djvu")))
    }
}