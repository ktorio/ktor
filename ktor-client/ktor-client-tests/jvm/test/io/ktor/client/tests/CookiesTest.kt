/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class CookiesTest : ClientLoader() {
    private val hostname = "http://127.0.0.1/cookies"
    private val TEST_HOST = "$TEST_SERVER/cookies"
    private val domain = "127.0.0.1"

    @Test
    fun testAccept() = clientTests {
        config {
            install(HttpCookies)
        }

        test { client ->
            client.get<Unit>(TEST_HOST)
            client.cookies(hostname).let {
                assertEquals(1, it.size)
                assertEquals("my-awesome-value", it["hello-cookie"]!!.value)
            }
        }
    }

    @Test
    fun testUpdate() = clientTests {
        config {
            install(HttpCookies) {
                default {
                    addCookie(hostname, Cookie("id", "1", domain = domain))
                }
            }
        }

        test { client ->
            repeat(10) {
                val before = client.getId()
                client.get<Unit>("$TEST_HOST/update-user-id")
                assertEquals(before + 1, client.getId())
                assertEquals("ktor", client.cookies(hostname)["user"]?.value)
            }
        }
    }

    @Test
    fun testExpiration() = clientTests {
        config {
            install(HttpCookies) {
                default {
                    addCookie(hostname, Cookie("id", "777", domain = domain, path = "/"))
                }
            }

            test { client ->
                assertFalse(client.cookies(hostname).isEmpty())
                client.get<Unit>("$TEST_HOST/expire")
                assertTrue(client.cookies(hostname).isEmpty(), "cookie should be expired")
            }
        }
    }

    @Test
    fun testConstant() = clientTests {
        config {
            install(HttpCookies) {
                storage = ConstantCookiesStorage(Cookie("id", "1", domain = domain))
            }
        }

        test { client ->
            repeat(3) {
                client.get<Unit>("$TEST_HOST/update-user-id")
                assertEquals(1, client.getId())
                assertNull(client.cookies(hostname)["user"]?.value)
            }
        }
    }

    @Test
    fun testMultipleCookies() {
        clientTests {
            config {
                install(HttpCookies) {
                    default {
                        addCookie(hostname, Cookie("first", "first-cookie", domain = domain))
                        addCookie(hostname, Cookie("second", "second-cookie", domain = domain))
                    }
                }
            }

            test { client ->
                val response = client.get<String>("$TEST_HOST/multiple")
                assertEquals("Multiple done", response)
            }
        }
    }

    @Test
    fun testPath() = clientTests {
        config {
            install(HttpCookies)
        }

        test { client ->
            assertEquals("OK", client.get("$TEST_HOST/withPath"))
            assertEquals("OK", client.get("$TEST_HOST/withPath/something"))
        }
    }

    @Test
    fun testWithLeadingDot() = clientTests {
        config {
            install(HttpCookies)
        }

        test { client ->
            client.get<Unit>("https://m.vk.com")
            assert(client.cookies("https://.vk.com").isNotEmpty())
            assert(client.cookies("https://vk.com").isNotEmpty())
            assert(client.cookies("https://m.vk.com").isNotEmpty())
            assert(client.cookies("https://m.vk.com").isNotEmpty())

            assert(client.cookies("https://google.com").isEmpty())
        }
    }

    @Test
    fun caseSensitive() = clientTests {
        config {
            install(HttpCookies)
        }

        test { client ->
            try {
                client.get<Unit>("$TEST_HOST/foo")
                client.get<Unit>("$TEST_HOST/FOO")
            } catch (cause: Throwable) {
                throw cause
            }
        }
    }

    private suspend fun HttpClient.getId() = cookies(hostname)["id"]!!.value.toInt()
}
