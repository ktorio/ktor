/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildMethodsTest {
    @Test
    fun postMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    respondOk()
                }
            }
        }

        client.post {
            assertEquals(HttpMethod.Post, method)
        }

        client.post("/") {
            assertEquals(HttpMethod.Post, method)
        }

        client.post {
            method = HttpMethod.Get
        }

        client.preparePost("/") {
            assertEquals(HttpMethod.Post, method)
        }.execute()

        client.preparePost {
            assertEquals(HttpMethod.Post, method)
        }.execute()
    }

    @Test
    fun putMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Put, request.method)
                    respondOk()
                }
            }
        }

        client.put {
            assertEquals(HttpMethod.Put, method)
        }

        client.put("/") {
            assertEquals(HttpMethod.Put, method)
        }

        client.put {
            method = HttpMethod.Get
        }

        client.preparePut("/") {
            assertEquals(HttpMethod.Put, method)
        }.execute()

        client.preparePut {
            assertEquals(HttpMethod.Put, method)
        }.execute()
    }

    @Test
    fun deleteMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Delete, request.method)
                    respondOk()
                }
            }
        }

        client.delete {
            assertEquals(HttpMethod.Delete, method)
        }

        client.delete("/") {
            assertEquals(HttpMethod.Delete, method)
        }

        client.delete {
            method = HttpMethod.Get
        }

        client.prepareDelete("/") {
            assertEquals(HttpMethod.Delete, method)
        }.execute()

        client.prepareDelete {
            assertEquals(HttpMethod.Delete, method)
        }.execute()
    }

    @Test
    fun optionsMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Options, request.method)
                    respondOk()
                }
            }
        }

        client.options {
            assertEquals(HttpMethod.Options, method)
        }

        client.options("/") {
            assertEquals(HttpMethod.Options, method)
        }

        client.options {
            method = HttpMethod.Get
        }

        client.prepareOptions("/") {
            assertEquals(HttpMethod.Options, method)
        }.execute()

        client.prepareOptions {
            assertEquals(HttpMethod.Options, method)
        }.execute()
    }

    @Test
    fun patchMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Patch, request.method)
                    respondOk()
                }
            }
        }

        client.patch {
            assertEquals(HttpMethod.Patch, method)
        }

        client.patch("/") {
            assertEquals(HttpMethod.Patch, method)
        }

        client.patch {
            method = HttpMethod.Get
        }

        client.preparePatch("/") {
            assertEquals(HttpMethod.Patch, method)
        }.execute()

        client.preparePatch {
            assertEquals(HttpMethod.Patch, method)
        }.execute()
    }

    @Test
    fun headMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Head, request.method)
                    respondOk()
                }
            }
        }

        client.head {
            assertEquals(HttpMethod.Head, method)
        }

        client.head("/") {
            assertEquals(HttpMethod.Head, method)
        }

        client.head {
            method = HttpMethod.Get
        }

        client.prepareHead("/") {
            assertEquals(HttpMethod.Head, method)
        }.execute()

        client.prepareHead {
            assertEquals(HttpMethod.Head, method)
        }.execute()
    }

    @Test
    fun getMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    respondOk()
                }
            }
        }

        client.get {
            assertEquals(HttpMethod.Get, method)
        }

        client.get("/") {
            assertEquals(HttpMethod.Get, method)
        }

        client.get {
            method = HttpMethod.Post
        }

        client.prepareGet("/") {
            assertEquals(HttpMethod.Get, method)
        }.execute()

        client.prepareGet {
            assertEquals(HttpMethod.Get, method)
        }.execute()
    }

    @Test
    fun queryMethodWithinBuilder() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Query, request.method)
                    respondOk()
                }
            }
        }

        client.query {
            assertEquals(HttpMethod.Query, method)
        }

        client.query("/") {
            assertEquals(HttpMethod.Query, method)
        }

        client.query {
            method = HttpMethod.Query
        }

        client.prepareQuery("/") {
            assertEquals(HttpMethod.Query, method)
        }.execute()

        client.prepareQuery {
            assertEquals(HttpMethod.Query, method)
        }.execute()
    }
}
