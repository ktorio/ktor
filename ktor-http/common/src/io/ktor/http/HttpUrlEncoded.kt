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
     * Form-url-encodes a list of key/value pairs into an application/x-www-form-urlencoded string.
     *
     * @param spaceToPlus If `true`, spaces are encoded as `+`; if `false`, spaces are encoded as `%20`. Defaults to `true`.
     * @return The encoded form string containing joined `key=value` pairs separated by `&`. Values that are `null` are serialized as keys without `=`.
     */
public fun List<Pair<String, String?>>.formUrlEncode(spaceToPlus: Boolean = true): String =
    buildString { formUrlEncodeTo(this, spaceToPlus) }

/**
 * Write this list of key/value pairs to the given Appendable using application/x-www-form-urlencoded encoding.
 *
 * Each pair is serialized as `key` or `key=value` and pairs are joined with `&`. A `null` value produces the key without an `=`. Keys and values are percent-encoded; when [spaceToPlus] is `true` space characters are encoded as `+`, otherwise as `%20`.
 *
 * @param out Destination appendable that receives the encoded form string.
 * @param spaceToPlus If `true`, encode space as `+`; if `false`, encode space as `%20`. Defaults to `true`.
 */
public fun List<Pair<String, String?>>.formUrlEncodeTo(out: Appendable, spaceToPlus: Boolean = true) {
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
     * Encodes parameters into an application/x-www-form-urlencoded string.
     *
     * @param spaceToPlus If `true`, space characters are encoded as `+`. If `false`, spaces are encoded as `%20`.
     * @return The resulting form-encoded string.
     */
public fun Parameters.formUrlEncode(spaceToPlus: Boolean = true): String = entries()
    .flatMap { e -> e.value.map { e.key to it } }
    .formUrlEncode(spaceToPlus)

/**
 * Write these parameters as application/x-www-form-urlencoded into the given appendable.
 *
 * @param out Destination to which the encoded form string is written.
 * @param spaceToPlus If `true`, encode space as `+`; if `false`, encode space as `%20`. Defaults to `true`.
 */
public fun Parameters.formUrlEncodeTo(out: Appendable, spaceToPlus: Boolean = true) {
    entries().formUrlEncodeTo(out, spaceToPlus)
}

/**
 * Write this builder's parameters to the given Appendable using application/x-www-form-urlencoded encoding.
 *
 * @param out Destination to which the encoded parameter string is written.
 * @param spaceToPlus If `true`, space characters are encoded as `'+'`; if `false`, spaces are percent-encoded (`"%20"`).
 */
internal fun ParametersBuilder.formUrlEncodeTo(out: Appendable, spaceToPlus: Boolean = true) {
    entries().formUrlEncodeTo(out, spaceToPlus)
}

/**
 * Writes the set of parameter entries to the provided Appendable as application/x-www-form-urlencoded data.
 *
 * Each map entry is expanded into one or more form fields: if the value list is empty a lone key is written;
 * otherwise one key=value pair is written for each value in the list. Pairs are encoded and joined with `&`.
 *
 * @param out Destination Appendable to write the encoded form string to.
 * @param spaceToPlus When `true`, spaces are encoded as `+`; when `false`, spaces are percent-encoded.
 */
internal fun Set<Map.Entry<String, List<String>>>.formUrlEncodeTo(out: Appendable, spaceToPlus: Boolean = true) {
    flatMap { (key, value) ->
        if (value.isEmpty()) listOf(key to null) else value.map { key to it }
    }.formUrlEncodeTo(out, spaceToPlus)
}