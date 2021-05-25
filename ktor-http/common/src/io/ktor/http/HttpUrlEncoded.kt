/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.utils.io.charsets.*

/**
 * Options for URL Encoding.
 * Keys and values are encoded only when [encodeKey] and [encodeValue] are `true` respectively.
 */
public enum class UrlEncodingOption(internal val encodeKey: Boolean, internal val encodeValue: Boolean) {
    DEFAULT(true, true),
    KEY_ONLY(true, false),
    VALUE_ONLY(false, true),
    NO_ENCODING(false, false)
}

/**
 * Parse URL query parameters. Shouldn't be used for urlencoded forms because of `+` character.
 */
public fun String.parseUrlEncodedParameters(defaultEncoding: Charset = Charsets.UTF_8, limit: Int = 1000): Parameters {
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
@Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
public fun List<Pair<String, String?>>.formUrlEncode(): String = formUrlEncode(UrlEncodingOption.DEFAULT)

/**
 * Encode form parameters from a list of pairs
 */
public fun List<Pair<String, String?>>.formUrlEncode(option: UrlEncodingOption = UrlEncodingOption.DEFAULT): String =
    buildString { formUrlEncodeTo(this, option) }

/**
 * Encode form parameters from a list of pairs to the specified [out] appendable
 */
@Suppress("unused")
@Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
public fun List<Pair<String, String?>>.formUrlEncodeTo(out: Appendable): Unit =
    formUrlEncodeTo(out, UrlEncodingOption.DEFAULT)

/**
 * Encode form parameters from a list of pairs to the specified [out] appendable by using [option]
 */
public fun List<Pair<String, String?>>.formUrlEncodeTo(
    out: Appendable,
    option: UrlEncodingOption = UrlEncodingOption.DEFAULT
) {
    joinTo(out, "&") {
        val key = if (option.encodeKey) it.first.encodeURLParameter(spaceToPlus = true) else it.first
        if (it.second == null) {
            key
        } else {
            val nonNullValue = it.second.toString()
            val value = if (option.encodeValue) nonNullValue.encodeURLParameterValue() else nonNullValue
            "$key=$value"
        }
    }
}

/**
 * Encode form parameters
 */
public fun Parameters.formUrlEncode(): String = entries()
    .flatMap { e -> e.value.map { e.key to it } }
    .formUrlEncode(urlEncodingOption)

/**
 * Encode form parameters to the specified [out] appendable
 */
public fun Parameters.formUrlEncodeTo(out: Appendable) {
    entries().formUrlEncodeTo(out, urlEncodingOption)
}

internal fun ParametersBuilder.formUrlEncodeTo(out: Appendable) {
    entries().formUrlEncodeTo(out, urlEncodingOption)
}

@Suppress("unused")
@Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
internal fun Set<Map.Entry<String, List<String>>>.formUrlEncodeTo(out: Appendable) =
    formUrlEncodeTo(out, UrlEncodingOption.DEFAULT)

internal fun Set<Map.Entry<String, List<String>>>.formUrlEncodeTo(
    out: Appendable,
    option: UrlEncodingOption = UrlEncodingOption.DEFAULT
) {
    flatMap { (key, value) ->
        if (value.isEmpty()) listOf(key to null) else value.map { key to it }
    }.formUrlEncodeTo(out, option)
}
