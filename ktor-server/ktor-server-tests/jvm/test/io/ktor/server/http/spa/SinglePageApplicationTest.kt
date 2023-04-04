/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.spa

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class SinglePageApplicationTest {
    @Test
    fun fullWithFilesTest() = testApplication {
        application {
            routing {
                singlePageApplication {
                    filesPath = "jvm/test/io/ktor/server/http/spa"
                    applicationRoute = "selected"
                    defaultPage = "Empty3.kt"
                    ignoreFiles { it.contains("Empty2.kt") }
                }
            }
        }

        client.get("/selected").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/selected/a").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/selected/Empty2.kt").let {
            assertEquals(it.status, HttpStatusCode.Forbidden)
        }

        client.get("/selected/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty1)
        }
    }

    @Test
    fun testPageGet() = testApplication {
        application {
            routing {
                singlePageApplication {
                    filesPath = "jvm/test/io/ktor/server/http/spa"
                    applicationRoute = "selected"
                    defaultPage = "Empty3.kt"
                }
            }
        }

        client.get("/selected/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty1)
        }

        client.get("/selected").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }
    }

    @Test
    fun testIgnoreAllRoutes() = testApplication {
        application {
            routing {
                singlePageApplication {
                    filesPath = "jvm/test/io/ktor/server/http/spa"
                    defaultPage = "Empty3.kt"
                    ignoreFiles { true }
                }
            }
        }

        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/a").let {
            assertEquals(it.status, HttpStatusCode.Forbidden)
        }

        client.get("/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.Forbidden)
        }
    }

    @Test
    fun fullWithResourcesTest() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "public"
                    applicationRoute = "selected"
                    defaultPage = "default.txt"
                    ignoreFiles { it.contains("ignore.txt") }
                }
            }
        }

        client.get("/selected").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "default")
        }

        client.get("/selected/a").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "default")
        }

        assertEquals(HttpStatusCode.Forbidden, client.get("/selected/ignore.txt").status)

        client.get("/selected/file.txt").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "file.txt")
        }
    }

    @Test
    fun testResources() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "public"
                    defaultPage = "default.txt"
                }
            }
        }

        client.get("/").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "default")
        }

        client.get("/a").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "default")
        }

        client.get("/file.txt").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "file.txt")
        }
    }

    @Test
    fun testIgnoreResourceRoutes() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "public"
                    defaultPage = "default.txt"
                    ignoreFiles { it.contains("ignore.txt") }
                    ignoreFiles { it.endsWith("file.txt") }
                }
            }
        }

        client.get("/index.html").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "index")
        }

        client.get("/a").let {
            assertEquals(HttpStatusCode.OK, it.status)
            assertEquals(it.bodyAsText().trimIndent(), "default")
        }

        client.get("/ignore.txt").let {
            assertEquals(HttpStatusCode.Forbidden, it.status)
        }

        client.get("/file.txt").let {
            assertEquals(HttpStatusCode.Forbidden, it.status)
        }
    }

    @Test
    fun testIgnoreAllResourceRoutes() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "public"
                    defaultPage = "default.txt"
                    ignoreFiles { true }
                }
            }
        }

        assertEquals(HttpStatusCode.Forbidden, client.get("/file.txt").status)

        client.get("/a").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals("default", it.bodyAsText().trimIndent())
        }
        client.get("/").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals("default", it.bodyAsText().trimIndent())
        }
    }

    @Test
    fun testShortcut() = testApplication {
        application {
            routing {
                singlePageApplication {
                    angular("jvm/test/io/ktor/server/http/spa")
                }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty1.kt").status)
    }

    private val empty1 = """
        /*
         * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
         */

        package io.ktor.server.http.spa

        // required for tests
        class Empty1
    """.trimIndent()

    private val empty3 = """
        /*
         * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
         */

        package io.ktor.server.http.spa

        // required for tests
        class Empty3
    """.trimIndent()
}
