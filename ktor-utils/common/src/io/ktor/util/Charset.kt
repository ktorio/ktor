package io.ktor.util

/**
 * Check if [Char] is in lower case
 */
@InternalAPI
fun Char.isLowerCase(): Boolean = toLowerCase() == this

/**
 * Convert [String] to [CharArray]
 */
@InternalAPI
fun String.toCharArray(): CharArray = CharArray(length) { get(it) }
