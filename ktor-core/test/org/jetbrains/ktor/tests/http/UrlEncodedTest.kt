package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.testing.*
import org.junit.*
import kotlin.test.*

class UrlEncodedTest {
    @Test
    fun `should parse simple with no headers`() {
        with(TestApplicationRequest()) {
            body = "field1=%D0%A2%D0%B5%D1%81%D1%82"

            val parsed = parseUrlEncodedParameters()
            assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"]?.single())
        }
    }

    @Test
    fun `should parse simple with no encoding`() {
        with(TestApplicationRequest()) {
            body = "field1=%D0%A2%D0%B5%D1%81%D1%82"
            addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")

            val parsed = parseUrlEncodedParameters()
            assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"]?.single())
        }
    }

    @Test
    fun `should parse simple with specified encoding utf8`() {
        with(TestApplicationRequest()) {
            body = "field1=%D0%A2%D0%B5%D1%81%D1%82"
            addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=utf-8")

            val parsed = parseUrlEncodedParameters()
            assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"]?.single())
        }
    }

    @Test
    fun `should parse simple with specified encoding non utf`() {
        with(TestApplicationRequest()) {
            body = "field1=%D2%E5%F1%F2"
            addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=windows-1251")

            val parsed = parseUrlEncodedParameters()
            assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"]?.single())
        }
    }

    @Test
    fun `should parse simple with specified encoding non utf in parameter`() {
        with(TestApplicationRequest()) {
            body = "field1=%D2%E5%F1%F2&_charset_=windows-1251"
            addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")

            val parsed = parseUrlEncodedParameters()
            assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"]?.single())
        }
    }
}