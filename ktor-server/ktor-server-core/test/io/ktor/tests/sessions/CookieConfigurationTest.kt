package io.ktor.tests.sessions

import io.ktor.sessions.*
import org.junit.Test
import kotlin.test.*

class CookieConfigurationTest {
    @Test
    fun sameSiteExtension() {
        fun configWith(value: CookieConfiguration.SameSite?) = CookieConfiguration().apply { this.sameSite = value }
        fun extConfigWith(value: CookieConfiguration.SameSite?) = configWith(value).extensions.toString()

        assertEquals("{}", CookieConfiguration().extensions.toString())
        assertEquals("{}", extConfigWith(null))
        assertEquals("{SameSite=lax}", extConfigWith(CookieConfiguration.SameSite.LAX))
        assertEquals("{SameSite=strict}", extConfigWith(CookieConfiguration.SameSite.STRICT))

        assertEquals(null, CookieConfiguration().sameSite)
        assertEquals(null, configWith(null).sameSite)
        assertEquals(CookieConfiguration.SameSite.LAX, configWith(CookieConfiguration.SameSite.LAX).sameSite)
        assertEquals(CookieConfiguration.SameSite.STRICT, configWith(CookieConfiguration.SameSite.STRICT).sameSite)
    }
}