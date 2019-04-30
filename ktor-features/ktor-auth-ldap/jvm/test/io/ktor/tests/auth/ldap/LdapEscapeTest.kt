/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth.ldap

import io.ktor.auth.ldap.*
import kotlin.test.*

class LdapEscapeTest {
    @Test
    fun smokeTest() {
        assertEquals("", ldapEscape(""))
        assertEquals("1", ldapEscape("1"))
        assertEquals("12", ldapEscape("12"))
        assertEquals("simple", ldapEscape("simple"))
        assertEquals("space\\ in\\ the\\ middle", ldapEscape("space in the middle"))
        assertEquals("\\ space\\ in\\ the\\ beginning", ldapEscape(" space in the beginning"))
        assertEquals("space\\ in\\ the\\ end\\ ", ldapEscape("space in the end "))

        assertEquals("slash\\\\quote\\\"hash\\#plus\\+comma\\,semi\\;less\\<eq\\=gt\\>",
            ldapEscape("slash\\quote\"hash#plus+comma,semi;less<eq=gt>"))

        assertEquals("null\\00", ldapEscape("null\u0000"))
    }

    @Test
    fun testUnicodeAndSpecials() {
        assertEquals("\\00", ldapEscape("\u0000"))
        assertEquals("\\d0\\96", ldapEscape("\u0416"))
    }
}
