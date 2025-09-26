/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.options
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareDelete
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareHead
import io.ktor.client.request.prepareOptions
import io.ktor.client.request.preparePatch
import io.ktor.client.request.preparePost
import io.ktor.client.request.preparePut
import io.ktor.client.request.put
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
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
}
