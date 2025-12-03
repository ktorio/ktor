/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.utils.io.charsets.*

/**
 * Parse URL query parameters. Shouldn't be used for urlencoded forms because of `+` character.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.parseUrlEncodedParameters)
 */
public fun String.parseUrlEncodedParameters(defaultEncoding: Charset = Charsets.UTF_8, limit: Int = 1000): Parameters {
    val parameters: List<Pair<String, String>> =
        split("&", limit = limit).map { it.substringBefore("=") to it.substringAfter("=", "") }
    val encoding: String =
        parameters.firstOrNull { it.first == "_charset_" }?.second ?: defaultEncoding.name

    val charset = Charsets.forName(encoding)
    return Parameters.build {
        parameters.forEach { (key, value) ->
            append(
                key.decodeURLQueryComponent(charset = charset),
                value.decodeURLQueryComponent(charset = charset)
            )
        }
    }
}

/**
 * Encode form parameters from a list of pairs
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.formUrlEncode)
 */
public fun List<Pair<String, String?>>.formUrlEncode(spaceToPlus: Boolean = false): String =
    buildString { formUrlEncodeTo(this, spaceToPlus) }

/**
 * Encode form parameters from a list of pairs to the specified [out] appendable
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.formUrlEncodeTo)
 */
public fun List<Pair<String, String?>>.formUrlEncodeTo(out: Appendable, spaceToPlus: Boolean = false) {
    joinTo(out, "&") {
        val key = it.first.encodeURLParameter(spaceToPlus = spaceToPlus)
        if (it.second == null) {
            key
        } else {
            val value = it.second.toString().encodeURLParameter(spaceToPlus = spaceToPlus)
            "$key=$value"
        }
    }
}

/**
 * Encode form parameters
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.formUrlEncode)
 */
public fun Parameters.formUrlEncode(spaceToPlus: Boolean = false): String = entries()
    .flatMap { e -> e.value.map { e.key to it } }
    .formUrlEncode(spaceToPlus)

/**
 * Encode form parameters to the specified [out] appendable
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.formUrlEncodeTo)
 */
public fun Parameters.formUrlEncodeTo(out: Appendable) {
    entries().formUrlEncodeTo(out)
}

internal fun ParametersBuilder.formUrlEncodeTo(out: Appendable) {
    entries().formUrlEncodeTo(out)
}

internal fun Set<Map.Entry<String, List<String>>>.formUrlEncodeTo(out: Appendable, spaceToPlus: Boolean = false) {
    flatMap { (key, value) ->
        if (value.isEmpty()) listOf(key to null) else value.map { key to it }
    }.formUrlEncodeTo(out, spaceToPlus)
}
