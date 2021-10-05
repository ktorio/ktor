import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.test.dispatcher.*
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

        assertEquals("http://localhost/", client.get {}.bodyAsText())
        assertEquals("http://localhost/", client.get("/").bodyAsText())
        assertEquals("http://localhost/file", client.get("file").bodyAsText())
        assertEquals("http://localhost/other_path", client.get("/other_path").bodyAsText())
        assertEquals("http://other.host/other_path", client.get("//other.host/other_path").bodyAsText())
    }

    @Test
    fun testDefaultHeader() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(it.headers["header-1"] + " " + it.headers["header-2"] )
                }
            }

            defaultRequest {
                headers["header-1"] = "value-default"
            }
        }

        assertEquals(
            "value-default value-2",
            client.get { headers["header-2"] = "value-2" }.bodyAsText()
        )
    }
}
