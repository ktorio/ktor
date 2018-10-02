package io.ktor.tests.sessions

import io.ktor.http.*
import io.ktor.sessions.*
import org.junit.Test
import kotlin.test.*

class CookieConfigurationTest {
    @Test
    fun sameSiteExtension() {
        fun configWith(value: CookieSameSite?) = CookieConfiguration().apply { this.sameSite = value }
        fun extConfigWith(value: CookieSameSite?) = configWith(value).extensions.toString()

        assertEquals("{}", CookieConfiguration().extensions.toString())
        assertEquals("{}", extConfigWith(null))
        assertEquals("{SameSite=lax}", extConfigWith(CookieSameSite.LAX))
        assertEquals("{SameSite=strict}", extConfigWith(CookieSameSite.STRICT))

        assertEquals(null, CookieConfiguration().sameSite)
        assertEquals(null, configWith(null).sameSite)
        assertEquals(CookieSameSite.LAX, configWith(CookieSameSite.LAX).sameSite)
        assertEquals(CookieSameSite.STRICT, configWith(CookieSameSite.STRICT).sameSite)

        val cookie = configWith(CookieSameSite.STRICT).buildCookie("hello", "world")
        assertEquals(CookieSameSite.STRICT, cookie.sameSite)
    }
}