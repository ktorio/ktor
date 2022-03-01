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
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
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
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }

        client.get("/Empty1.kt").let {
            assertEquals(it.status, HttpStatusCode.OK)
            assertEquals(it.bodyAsText().trimIndent(), empty3)
        }
    }

    @Test
    fun fullWithResourcesTest() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "io.ktor.server.http.spa"
                    applicationRoute = "selected"
                    defaultPage = "Empty3.class"
                    ignoreFiles { it.contains("Empty2.class") }
                }
            }
        }
        assertEquals(client.get("/selected").status, HttpStatusCode.OK)
        assertEquals(client.get("/selected/a").status, HttpStatusCode.OK)
        assertEquals(client.get("/selected/Empty2.kt").status, HttpStatusCode.OK)
        assertEquals(client.get("/selected/Empty1.kt").status, HttpStatusCode.OK)
    }

    @Test
    fun testResources() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "io.ktor.server.http.spa"
                    defaultPage = "SinglePageApplicationTest.class"
                }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/SinglePageApplicationTest.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/a").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun testIgnoreResourceRoutes() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "io.ktor.server.http.spa"
                    defaultPage = "SinglePageApplicationTest.class"
                    ignoreFiles { it.contains("Empty1.class") }
                    ignoreFiles { it.endsWith("Empty2.class") }
                }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/Empty3.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
        assertEquals(HttpStatusCode.OK, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/Empty2.class").status)
    }

    @Test
    fun testIgnoreAllResourceRoutes() = testApplication {
        application {
            routing {
                singlePageApplication {
                    useResources = true
                    filesPath = "io.ktor.server.http.spa"
                    defaultPage = "SinglePageApplicationTest.class"
                    ignoreFiles { true }
                }
            }
        }

        assertEquals(HttpStatusCode.OK, client.get("/SinglePageApplicationTest.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/Empty1.class").status)
        assertEquals(HttpStatusCode.OK, client.get("/a").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
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
