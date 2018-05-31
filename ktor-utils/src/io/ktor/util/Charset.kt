package io.ktor.util

/**
 * Check if [Char] is in lower case
 */
fun Char.isLowerCase(): Boolean = toLowerCase() == this

/**
 * Convert [String] to [CharArray]
 */
fun String.toCharArray(): CharArray = CharArray(length) { get(it) }
