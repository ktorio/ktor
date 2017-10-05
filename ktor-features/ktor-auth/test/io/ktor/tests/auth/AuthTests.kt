package io.ktor.tests.auth

import io.ktor.auth.*
import java.util.*
import kotlin.test.*
import org.junit.Test as test

class AuthorizeHeaderParserTest {
    @org.junit.Test fun empty() {
        testParserParameterized("Basic", emptyMap(), "Basic")
    }

    @org.junit.Test fun emptyWithTrailingSpaces() {
        testParserParameterized("Basic", emptyMap(), "Basic ")
    }

    @org.junit.Test fun singleSimple() {
        testParserSingle("Basic", "abc==", "Basic abc==")
    }

    @org.junit.Test fun testParameterizedSimple() {
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a=1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a =1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a = 1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a= 1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic  a=1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a=1 ")
    }

    @org.junit.Test fun testParameterizedSimpleTwoParams() {
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1, b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1,b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 ,b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 , b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 , b=2 ")
    }

    @org.junit.Test fun testParameterizedQuoted() {
        testParserParameterized("Basic", mapOf("a" to "1 2"), "Basic a=\"1 2\"")
    }

    @org.junit.Test fun testParameterizedQuotedEscaped() {
        testParserParameterized("Basic", mapOf("a" to "1 \" 2"), "Basic a=\"1 \\\" 2\"")
        testParserParameterized("Basic", mapOf("a" to "1 A 2"), "Basic a=\"1 \\A 2\"")
    }

    @org.junit.Test fun testParameterizedQuotedEscapedInTheMiddle() {
        testParserParameterized("Basic", mapOf("a" to "1 \" 2", "b" to "2"), "Basic a=\"1 \\\" 2\", b= 2")
    }

    @org.junit.Test fun testGarbage() {
        Random().let { rnd ->
            repeat(10000) {
                val random = rnd.nextString(3 + rnd.nextInt(7)) + " " + rnd.nextString(10, ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(',', ' ', '/'))
                parseAuthorizationHeader(random)
            }
        }
    }

    private fun testParserSingle(scheme: String, value: String, headerValue: String) {
        val actual = parseAuthorizationHeader(headerValue)!!

        assertEquals(scheme, actual.authScheme)

        if (actual is HttpAuthHeader.Single) {
            assertEquals(value, actual.blob)
        } else {
            fail("It should return single-value credential")
        }
    }

    private fun testParserParameterized(scheme: String, value: Map<String, String>, headerValue: String) {
        val actual = parseAuthorizationHeader(headerValue)!!

        assertEquals(scheme, actual.authScheme)

        if (actual is HttpAuthHeader.Parameterized) {
            assertEquals(value, actual.parameters.associateBy({ it.name }, { it.value }))
        } else {
            fail("It should return parameterized-value credential")
        }
    }

    private fun Random.nextString(length: Int, possible: Iterable<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')) = possible.toList().let { possibleElements ->
        (0..length - 1).map { nextFrom(possibleElements) }.joinToString("")
    }

    private fun Random.nextString(length: Int, possible: String) = nextString(length, possible.toList())

    private fun <T> Random.nextFrom(possibleElements: List<T>): T =
            if (possibleElements.isEmpty()) throw NoSuchElementException()
            else possibleElements[nextInt(possibleElements.size)]
}
