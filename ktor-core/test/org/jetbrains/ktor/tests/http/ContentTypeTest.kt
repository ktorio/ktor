package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class ContentTypeTest {

    Test fun `ContentType text-plain`() {
        val ct = ContentType.Text.Plain
        on("parsing parts") {
            it("should have text contentType") {
                assertEquals("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                assertEquals("plain", ct.contentSubtype)
            }
            it("should have 0 parameters") {
                assertEquals(0, ct.parameters.size())
            }
        }
    }

    Test fun `text-plain`() {
        val ct = ContentType.parse("text/plain")
        on("parsing parts") {
            it("should have text contentType") {
                assertEquals("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                assertEquals("plain", ct.contentSubtype)
            }
            it("should have 0 parameters") {
                assertEquals(0, ct.parameters.size())
            }
        }
    }

    Test fun `text-plain charset is utf-8`() {
        val ct = ContentType.parse("text/plain ; charset = utf-8")
        on("parsing content") {
            it("should have text contentType") {
                assertEquals("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                assertEquals("plain", ct.contentSubtype)
            }
            it("should have 1 parameters") {
                assertEquals(1, ct.parameters.size())
            }
            it("should have utf-8 charset") {
                assertEquals(ContentTypeParameter("charset", "utf-8"), ct.parameters[0])
            }

        }
        on("doing a toString") {
            val toString = ct.toString()
            it("should strip unnecessary spaces") {
                assertEquals("text/plain; charset=utf-8", toString)
            }
        }
        on("comparing to content type with parameters") {
            it("should be equal") {
                assertTrue(ContentType.Text.Plain.withParameter("charset", "utf-8") == ct)
            }
        }
    }

    Test fun `text-plain charset is utf-8 with parameter foo-bar`() {
        val ct = ContentType.parse("text/plain ; charset = utf-8;foo=bar")

        on("doing a toString") {
            val toString = ct.toString()
            it("should add required spaces") {
                assertEquals("text/plain; charset=utf-8; foo=bar", toString)
            }
        }
    }

    Test fun `text-plain-invalid`() {
        on("parsing text/plain/something") {
            val error = fails {
                ContentType.Companion.parse("text/plain/something")
            }
            it("should throw BadContentTypeFormat exception") {
                assertEquals(javaClass<BadContentTypeFormatException>(), error?.javaClass)

            }
        }
    }

    Test
    fun `content type with empty parameters block`() {
        on("parsing empty parameters block so we have trailing semicolon and possibly whitespaces") {
            it("shouldn't fail and should pass equality checks") {
                assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain; "))
                assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain;"))
            }
        }
    }
}
