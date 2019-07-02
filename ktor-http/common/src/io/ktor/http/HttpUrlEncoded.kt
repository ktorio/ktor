/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*
import io.ktor.utils.io.charsets.*

/**
 * Parse URL query parameters. Shouldn't be used for urlencoded forms because of `+` character.
 */
@KtorExperimentalAPI
fun String.parseUrlEncodedParameters(defaultEncoding: Charset = Charsets.UTF_8, limit: Int = 1000): Parameters {
    val parameters: List<Pair<String, String>> =
        split("&", limit = limit).map { it.substringBefore("=") to it.substringAfter("=", "") }
    val encoding: String =
        parameters.firstOrNull { it.first == "_charset_" }?.second ?: defaultEncoding.name

    val charset = Charset.forName(encoding)
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
 */
fun List<Pair<String, String?>>.formUrlEncode(): String = StringBuilder().apply {
    formUrlEncodeTo(this)
}.toString()

/**
 * Encode form parameters from a list of pairs to the specified [out] appendable
 */
fun List<Pair<String, String?>>.formUrlEncodeTo(out: Appendable) {
    joinTo(
        out, "&"
    ) {
        val key = it.first.encodeURLParameter(spaceToPlus = true)
        if (it.second == null) {
            key
        }
        else {
            val value = it.second.toString().encodeURLParameter(spaceToPlus = true)
            "$key=$value"
        }
    }
}

/**
 * Encode form parameters
 */
fun Parameters.formUrlEncode(): String = entries()
    .flatMap { e -> e.value.map { e.key to it } }
    .formUrlEncode()

/**
 * Encode form parameters to the specified [out] appendable
 */
fun Parameters.formUrlEncodeTo(out: Appendable) {
    entries()
        .flatMap { e -> if (e.value.isEmpty()) listOf(e.key to null) else e.value.map { e.key to it } }
        .formUrlEncodeTo(out)
}
