package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.testing.*
import org.junit.*
import kotlin.test.*

class ContentTypeTest {

    @Test fun `ContentType text-plain`() {
        val ct = ContentType.Text.Plain
        on("parsing parts") {
            it("should have text contentType") {
                assertEquals("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                assertEquals("plain", ct.contentSubtype)
            }
            it("should have 0 parameters") {
                assertEquals(0, ct.parameters.size)
            }
        }
    }

    @Test fun `text-plain`() {
        val ct = ContentType.parse("text/plain")
        on("parsing parts") {
            it("should have text contentType") {
                assertEquals("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                assertEquals("plain", ct.contentSubtype)
            }
            it("should have 0 parameters") {
                assertEquals(0, ct.parameters.size)
            }
        }
    }

    @Test fun `text-plain charset in quotes`() {
        val ct1 = ContentType.parse("text/plain; charset=us-ascii")
        val ct2 = ContentType.parse("text/plain; charset=\"us-ascii\"")
        on("parsing parts") {
            it("parameters in quotes are equivalent to those without") {
                assertEquals(ct1, ct2)
            }
        }
    }

    @Test fun `text-plain charset case insensitive`() {
        val ct1 = ContentType.parse("Text/plain; charset=UTF-8")
        val ct2 = ContentType.parse("text/Plain; CHARSET=utf-8")
        on("parsing parts") {
            it("parameters in quotes are equivalent to those without") {
                assertEquals(ct1, ct2)
            }
        }
    }

    @Test fun `text-plain charset is utf-8`() {
        val ct = ContentType.parse("text/plain ; charset = utf-8")
        on("parsing content") {
            it("should have text contentType") {
                assertEquals("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                assertEquals("plain", ct.contentSubtype)
            }
            it("should have 1 parameters") {
                assertEquals(1, ct.parameters.size)
            }
            it("should have utf-8 charset") {
                assertEquals(HeaderValueParam("charset", "utf-8"), ct.parameters[0])
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
                assertEquals(ContentType.Text.Plain.withParameter("charset", "utf-8"), ct)
            }
        }
    }

    @Test fun `text-plain charset is utf-8 with parameter foo-bar`() {
        val ct = ContentType.parse("text/plain ; charset = utf-8;foo=bar")

        on("doing a toString") {
            val toString = ct.toString()
            it("should add required spaces") {
                assertEquals("text/plain; charset=utf-8; foo=bar", toString)
            }
        }
    }

    @Test fun `text-plain-invalid`() {
        on("parsing text/plain/something") {
            it("should throw BadContentTypeFormat exception") {
                assertFailsWith(BadContentTypeFormatException::class) {
                    ContentType.parse("text/plain/something")
                }
            }
        }
    }

    @Test
    fun `content type with empty parameters block`() {
        on("parsing empty parameters block so we have trailing semicolon and possibly whitespaces") {
            it("shouldn't fail and should pass equality checks") {
                assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain; "))
                assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain;"))
            }
        }
    }

    @Test
    fun `content type render works`() {
        // rendering tests are in [HeadersTest] so it is just a smoke test
        on("render content type with parameters") {
            it("should be able to render something") {
                assertEquals("text/plain; p1=v1", ContentType.Text.Plain.withParameter("p1", "v1").toString())
            }
        }
    }
}
