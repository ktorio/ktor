package org.jetbrains.ktor.tests.http.auth.tests

import org.jetbrains.ktor.http.auth.*
import kotlin.test.*
import org.junit.Test as test

class AuthorizeHeaderParserTest {
    @test fun empty() {
        testParserParameterized("Basic", emptyMap(), "Basic")
    }

    @test fun emptyWithTrailingSpaces() {
        testParserParameterized("Basic", emptyMap(), "Basic ")
    }

    @test fun singleSimple() {
        testParserSingle("Basic", "abc==", "Basic abc==")
    }

    @test fun testParameterizedSimple() {
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a=1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a =1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a = 1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a= 1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic  a=1")
        testParserParameterized("Basic", mapOf("a" to "1"), "Basic a=1 ")
    }

    @test fun testParameterizedSimpleTwoParams() {
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1, b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1,b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 ,b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 , b=2")
        testParserParameterized("Basic", mapOf("a" to "1", "b" to "2"), "Basic a=1 , b=2 ")
    }

    @test fun testParameterizedQuoted() {
        testParserParameterized("Basic", mapOf("a" to "1 2"), "Basic a=\"1 2\"")
    }

    @test fun testParameterizedQuotedEscaped() {
        testParserParameterized("Basic", mapOf("a" to "1 \" 2"), "Basic a=\"1 \\\" 2\"")
        testParserParameterized("Basic", mapOf("a" to "1 A 2"), "Basic a=\"1 \\A 2\"")
    }

    @test fun testParameterizedQuotedEscapedInTheMiddle() {
        testParserParameterized("Basic", mapOf("a" to "1 \" 2", "b" to "2"), "Basic a=\"1 \\\" 2\", b= 2")
    }

    private fun testParserSingle(scheme: String, value: String, headerValue: String) {
        val actual = parseAuthorizationHeader(headerValue)!!

        assertEquals(scheme, actual.authScheme)

        if (actual is HttpAuthCredentials.Single) {
            assertEquals(value, actual.blob)
        } else {
            fail("It should return single-value credential")
        }
    }

    private fun testParserParameterized(scheme: String, value: Map<String, String>, headerValue: String) {
        val actual = parseAuthorizationHeader(headerValue)!!

        assertEquals(scheme, actual.authScheme)

        if (actual is HttpAuthCredentials.Parameterized) {
            assertEquals(value, actual.parameters)
        } else {
            fail("It should return parameterized-value credential")
        }
    }
}
