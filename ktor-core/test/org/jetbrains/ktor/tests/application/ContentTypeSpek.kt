package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.http.*
import org.jetbrains.spek.api.*
import kotlin.test.*

class ContentTypeSpek : Spek() {init {

    given("ContentType text/plain") {
        val ct = ContentType.Text.Plain
        on("parsing parts") {
            it("should have text contentType") {
                shouldEqual("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                shouldEqual("plain", ct.contentSubtype)
            }
            it("should have 0 parameters") {
                shouldEqual(0, ct.parameters.size())
            }
        }
    }

    given("text/plain") {
        val ct = ContentType.parse("text/plain")
        on("parsing parts") {
            it("should have text contentType") {
                shouldEqual("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                shouldEqual("plain", ct.contentSubtype)
            }
            it("should have 0 parameters") {
                shouldEqual(0, ct.parameters.size())
            }
        }
    }

    given("text/plain ; charset = utf-8") {
        val ct = ContentType.parse("text/plain ; charset = utf-8")
        on("parsing content") {
            it("should have text contentType") {
                shouldEqual("text", ct.contentType)
            }
            it("should have plain contentSubType") {
                shouldEqual("plain", ct.contentSubtype)
            }
            it("should have 1 parameters") {
                shouldEqual(1, ct.parameters.size())
            }
            it("should have utf-8 charset") {
                shouldEqual("charset" to "utf-8", ct.parameters[0])
            }

        }
        on("doing a toString") {
            val toString = ct.toString()
            it("should strip unnecessary spaces") {
                shouldEqual("text/plain; charset=utf-8", toString)
            }
        }
        on("comparing to content type with parameters") {
            it("should be equal") {
                shouldBeTrue(ContentType.Text.Plain.withParameter("charset", "utf-8") == ct)
            }
        }
    }

    given("text/plain ; charset = utf-8;foo=bar") {
        val ct = ContentType.parse("text/plain ; charset = utf-8;foo=bar")

        on("doing a toString") {
            val toString = ct.toString()
            it("should add required spaces") {
                shouldEqual("text/plain; charset=utf-8; foo=bar", toString)
            }
        }
    }

    given("text/plain/invalid") {
        on("parsing parts") {
            val error = fails {
                ContentType.Companion.parse("text/plain/something")
            }
            it("should throw BadContentTypeFormat exception") {
                shouldEqual(javaClass<BadContentTypeFormat>(), error?.javaClass)

            }
        }
    }
}
}
