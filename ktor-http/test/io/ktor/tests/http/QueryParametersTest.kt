package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.util.*
import org.junit.Test
import kotlin.test.*

class QueryParametersTest {

    @Test
    fun oauthTest() {
        val parameters = parseQueryString("redirected=true&oauth_token=token1&oauth_verifier=verifier1")
        val built = ValuesMap.build {
            append("redirected", "true")
            append("oauth_token", "token1")
            append("oauth_verifier", "verifier1")
        }
        assertEquals(built, parameters)
    }

    @Test
    fun escapePlusTest() {
        val parameters = parseQueryString("id=1&optional=ok%2B.plus")
        val built = ValuesMap.build {
            append("id", "1")
            append("optional", "ok+.plus")
        }
        assertEquals(built, parameters)
    }

    @Test
    fun escapeSpaceTest() {
        val parameters = parseQueryString("id=1&optional=ok+space")
        val built = ValuesMap.build {
            append("id", "1")
            append("optional", "ok space")
        }
        assertEquals(built, parameters)
    }

    @Test
    fun skipSpaces() {
        val parameters = parseQueryString(" id=1 & optional= ok+space")
        val built = ValuesMap.build {
            append("id", "1")
            append("optional", "ok space")
        }
        assertEquals(built, parameters)
    }

    @Test
    fun noValue() {
        val parameters = parseQueryString("id&optional")
        val built = ValuesMap.build {
            append("id", "")
            append("optional", "")
        }
        assertEquals(built, parameters)
    }

    @Test
    fun doubleEquals() {
        val parameters = parseQueryString("id=1=2")
        val built = ValuesMap.build {
            append("id", "1=2")
        }
        assertEquals(built, parameters)
    }
}