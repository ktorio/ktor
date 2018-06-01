package io.ktor.compat

import kotlinx.io.charsets.*
import java.util.*

actual fun Char.isLowerCase(): Boolean = Character.isLowerCase(this)

actual fun String.toCharArray(): CharArray = (this as java.lang.String).toCharArray()

actual fun encodeBase64(string: String, charset: Charset): String =
    Base64.getEncoder().encodeToString(string.toByteArray(charset))

actual fun decodeBase64(string: String, charset: Charset): String =
    String(Base64.getDecoder().decode(string), charset = charset)