/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FilteredCookiesStorageTest {

    @Test
    fun testStorageWithoutPoliciesMatchesAcceptAllStorage() = runTest {
        val requestUrl = Url("https://www.example.com/path")
        val cookies = listOf(
            Cookie("host", "value"),
            Cookie("parent", "value", domain = "example.com", path = "/"),
            Cookie("unrelated", "value", domain = "unrelated.example", path = "/")
        )
        val expected = AcceptAllCookiesStorage()
        val actual = FilteredCookiesStorage()

        for (cookie in cookies) {
            expected.addCookie(requestUrl, cookie)
            actual.addCookie(requestUrl, cookie)
        }

        assertEquals(expected.get(requestUrl), actual.get(requestUrl))
        assertEquals(expected.get(Url("https://unrelated.example/")), actual.get(Url("https://unrelated.example/")))
    }

    @Test
    fun testPoliciesAreSuspendingAndRunInOrder() = runTest {
        val calls = mutableListOf<Int>()
        val storage = FilteredCookiesStorage(
            { _, _ ->
                yield()
                calls += 1
                true
            },
            { _, _ ->
                yield()
                calls += 2
                true
            }
        )
        val requestUrl = Url("https://example.com/")
        storage.addCookie(requestUrl, Cookie("name", "value"))

        assertEquals(listOf(1, 2), calls)
        assertEquals("value", storage.get(requestUrl).single().value)
    }

    @Test
    fun testRejectedCookieStopsPolicyEvaluation() = runTest {
        var subsequentPolicyCalled = false
        val storage = FilteredCookiesStorage(
            { _, _ -> false },
            { _, _ -> true.also { subsequentPolicyCalled = it } }
        )
        val requestUrl = Url("https://example.com/")

        storage.addCookie(requestUrl, Cookie("name", "value"))

        assertFalse(subsequentPolicyCalled)
        assertTrue(storage.get(requestUrl).isEmpty())
    }

    @Test
    fun testPolicyExceptionPropagatesWithoutMutatingStorage() = runTest {
        val requestUrl = Url("https://example.com/")
        val storage = FilteredCookiesStorage(
            { _, cookie ->
                if (cookie.value == "replacement") error("Policy failed")
                true
            }
        )
        storage.addCookie(requestUrl, Cookie("session", "original"))

        val cause = assertFailsWith<IllegalStateException> {
            storage.addCookie(requestUrl, Cookie("session", "replacement"))
        }

        assertEquals("Policy failed", cause.message)
        assertEquals("original", storage.get(requestUrl).single().value)
    }

    @Test
    fun testPolicyCancellationPropagates() = runTest {
        val storage = FilteredCookiesStorage({ _, _ -> throw CancellationException("Policy cancelled") })

        val cause = assertFailsWith<CancellationException> {
            storage.addCookie(Url("https://example.com/"), Cookie("name", "value"))
        }

        assertEquals("Policy cancelled", cause.message)
    }

    @Test
    fun testMandatoryValidationRunsBeforePolicies() = runTest {
        var policyCalled = false
        val storage = FilteredCookiesStorage({ _, _ -> true.also { policyCalled = it } })

        val url = Url("https://evil.com/")
        val cookie = Cookie("session", "injected", domain = "victim.com", path = "/")
        storage.addCookie(url, cookie)

        assertFalse(policyCalled)
        assertTrue(storage.get(Url("https://victim.com/")).isEmpty())
    }

    @Test
    fun testRejectedCookieDoesNotReplaceExistingCookie() = runTest {
        val requestUrl = Url("https://example.com/")
        val storage = FilteredCookiesStorage({ _, cookie -> cookie.value != "replacement" })

        storage.addCookie(requestUrl, Cookie("session", "original"))
        storage.addCookie(requestUrl, Cookie("session", "replacement"))

        assertEquals("original", storage.get(requestUrl).single().value)
    }
}
