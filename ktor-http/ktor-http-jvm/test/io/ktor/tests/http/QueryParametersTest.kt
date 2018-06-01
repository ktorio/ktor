package io.ktor.tests.http

import io.ktor.http.*
import org.junit.Test
import kotlin.test.*

class QueryParametersTest {

    fun assertQuery(query: String, startIndex: Int = 0, limit: Int = 1000, builder: ParametersBuilder.() -> Unit) {
        val parameters = parseQueryString(query, startIndex, limit)
        val built = Parameters.build(builder)
        assertEquals(built, parameters)
    }

    @Test
    fun oauthTest() {
        assertQuery("redirected=true&oauth_token=token1&oauth_verifier=verifier1") {
            append("redirected", "true")
            append("oauth_token", "token1")
            append("oauth_verifier", "verifier1")
        }
    }

    @Test
    fun emptyTest() {
        assertQuery("") {}
        assertQuery("   ") {}
    }

    @Test
    fun escapePlusTest() {
        assertQuery("id=1&optional=ok%2B.plus") {
            append("id", "1")
            append("optional", "ok+.plus")
        }
    }

    @Test
    fun escapeSpaceTest() {
        assertQuery("id=1&optional=ok+space") {
            append("id", "1")
            append("optional", "ok space")
        }
        assertQuery("id=1&optional=ok+") {
            append("id", "1")
            append("optional", "ok ")
        }
    }

    @Test
    fun skipSpaces() {
        assertQuery(" id=1 & optional= ok+space") {
            append("id", "1")
            append("optional", "ok space")
        }
    }

    @Test
    fun noValue() {
        assertQuery("id") {
            append("id", "")
        }
        assertQuery("id&optional") {
            append("id", "")
            append("optional", "")
        }
    }

    @Test
    fun doubleEquals() {
        assertQuery("id=1=2") {
            append("id", "1=2")
        }
    }

    @Test
    fun startIndexText() {
        assertQuery("?id=1&optional=ok%2B.plus", 1) {
            append("id", "1")
            append("optional", "ok+.plus")
        }
        assertQuery("?id=1&optional=ok%2B.plus", 6) {
            append("optional", "ok+.plus")
        }
    }

    @Test
    fun limitTest() {
        assertQuery("redirected=true&oauth_token=token1&oauth_verifier=verifier1", limit = 1) {
            append("redirected", "true")
        }
        assertQuery("redirected=true&oauth_token=token1&oauth_verifier=verifier1", limit = 2) {
            append("redirected", "true")
            append("oauth_token", "token1")
        }
    }

    @Test
    fun brokenTest() {
        assertQuery("&&&&") {}
        assertQuery("&=&=&") {}
    }
}