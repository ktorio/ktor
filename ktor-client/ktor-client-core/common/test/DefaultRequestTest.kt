import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlin.native.concurrent.*
import kotlin.test.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class DefaultRequestTest {

    @Test
    fun testDefaultPathWithDir() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {
                url("http://base.url/path/")
            }
        }

        assertEquals("http://base.url/path/", client.get { }.bodyAsText())
        assertEquals("http://base.url/", client.get("/").bodyAsText())
        assertEquals("http://base.url/path/file", client.get("file").bodyAsText())
        assertEquals("http://base.url/other_path", client.get("/other_path").bodyAsText())
        assertEquals("http://other.host/other_path", client.get("//other.host/other_path").bodyAsText())
    }

    @Test
    fun testDefaultPathWithFile() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {
                url("http://base.url/path/default_file")
            }
        }

        assertEquals("http://base.url/path/default_file", client.get {}.bodyAsText())
        assertEquals("http://base.url/", client.get("/").bodyAsText())
        assertEquals("http://base.url/path/file", client.get("file").bodyAsText())
        assertEquals("http://base.url/other_path", client.get("/other_path").bodyAsText())
        assertEquals("http://other.host/other_path", client.get("//other.host/other_path").bodyAsText())
    }

    @Test
    fun testDefaultWithoutUrl() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.url.toString())
                }
            }

            defaultRequest {}
        }

        assertEquals("${URLBuilder.Companion.origin}/", client.get {}.bodyAsText())
        assertEquals("${URLBuilder.Companion.origin}/", client.get("/").bodyAsText())
        assertEquals("${URLBuilder.Companion.origin}/file", client.get("file").bodyAsText())
        assertEquals("${URLBuilder.Companion.origin}/other_path", client.get("/other_path").bodyAsText())
        assertEquals("http://other.host/other_path", client.get("//other.host/other_path").bodyAsText())
    }

    @Test
    fun testDefaultHeader() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.headers.getAll("header-1")!!.joinToString() + ", " + it.headers["header-2"])
                }
            }

            defaultRequest {
                headers.append("header-1", "value-default-1")
                headers.appendIfNameAbsent("header-2", "value-default-2")
            }
        }

        assertEquals(
            "value-1, value-default-1, value-2",
            client.get {
                headers["header-1"] = "value-1" // appends
                headers["header-2"] = "value-2" // sets
            }.bodyAsText()
        )
    }

    @Test
    fun testDefaultAttributes() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.attributes[TestAttributeKey])
                }
            }

            defaultRequest {
                setAttributes { put(TestAttributeKey, "default-string") }
            }
        }

        assertEquals("default-string", client.get { }.bodyAsText())
        assertEquals("custom-string", client.get { attributes.put(TestAttributeKey, "custom-string") }.bodyAsText())
    }
}

@SharedImmutable
private val TestAttributeKey = AttributeKey<String>("TestAttributeKey")
