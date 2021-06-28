/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*

internal class UrlDecodedParametersBuilder(
    private val encodedParametersBuilder: ParametersBuilder
) : ParametersBuilder {

    override fun build(): Parameters = decodeParameters(encodedParametersBuilder)

    override val caseInsensitiveName: Boolean = encodedParametersBuilder.caseInsensitiveName

    override fun getAll(name: String): List<String>? = encodedParametersBuilder.getAll(name.encodeURLParameter())
        ?.map { it.decodeURLQueryComponent(plusIsSpace = true) }

    override fun contains(name: String): Boolean = encodedParametersBuilder.contains(name.encodeURLParameter())

    override fun contains(name: String, value: String): Boolean =
        encodedParametersBuilder.contains(name.encodeURLParameter(), value.encodeURLParameterValue())

    override fun names(): Set<String> =
        encodedParametersBuilder.names().map { it.decodeURLQueryComponent() }.toSet()

    override fun isEmpty(): Boolean = encodedParametersBuilder.isEmpty()

    override fun entries(): Set<Map.Entry<String, List<String>>> = decodeParameters(encodedParametersBuilder).entries()

    override fun set(name: String, value: String) =
        encodedParametersBuilder.set(name.encodeURLParameter(), value.encodeURLParameterValue())

    override fun get(name: String): String? =
        encodedParametersBuilder[name.encodeURLParameter()]?.decodeURLQueryComponent(plusIsSpace = true)

    override fun append(name: String, value: String) =
        encodedParametersBuilder.append(name.encodeURLParameter(), value.encodeURLParameterValue())

    override fun appendAll(stringValues: StringValues) = encodedParametersBuilder.appendAllEncoded(stringValues)

    override fun appendAll(name: String, values: Iterable<String>) =
        encodedParametersBuilder.appendAll(name.encodeURLParameter(), values.map { it.encodeURLParameterValue() })

    override fun appendMissing(stringValues: StringValues) =
        encodedParametersBuilder.appendMissing(encodeParameters(stringValues).build())

    override fun appendMissing(name: String, values: Iterable<String>) =
        encodedParametersBuilder.appendMissing(name.encodeURLParameter(), values.map { it.encodeURLParameterValue() })

    override fun remove(name: String) =
        encodedParametersBuilder.remove(name.encodeURLParameter())

    override fun remove(name: String, value: String): Boolean =
        encodedParametersBuilder.remove(name.encodeURLParameter(), value.encodeURLParameterValue())

    override fun removeKeysWithNoEntries() = encodedParametersBuilder.removeKeysWithNoEntries()

    override fun clear() = encodedParametersBuilder.clear()
}

internal fun decodeParameters(parameters: StringValuesBuilder): Parameters = ParametersBuilder()
    .apply { appendAllDecoded(parameters) }
    .build()

internal fun encodeParameters(parameters: StringValues): ParametersBuilder = ParametersBuilder()
    .apply { appendAllEncoded(parameters) }

private fun StringValuesBuilder.appendAllDecoded(parameters: StringValuesBuilder) {
    parameters.names()
        .forEach { key ->
            val values = parameters.getAll(key) ?: emptyList()
            appendAll(
                key.decodeURLQueryComponent(),
                values.map { it.decodeURLQueryComponent(plusIsSpace = true) }
            )
        }
}

private fun StringValuesBuilder.appendAllEncoded(parameters: StringValues) {
    parameters.names()
        .forEach { key ->
            val values = parameters.getAll(key) ?: emptyList()
            appendAll(key.encodeURLParameter(), values.map { it.encodeURLParameterValue() })
        }
}
