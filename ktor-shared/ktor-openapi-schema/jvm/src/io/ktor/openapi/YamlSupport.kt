/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import com.charleskorn.kaml.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * [GenericElementSerialAdapter] for the `kaml` YAML processing library.
 */
internal object YamlNodeSerialAdapter : GenericElementSerialAdapter {

    // kaml doesn't include a function for encoding to YamlNode,
    // so we just return a GenericElementWrapper
    override fun <T> trySerializeToElement(
        encoder: Encoder,
        value: T,
        serializer: KSerializer<T>
    ): GenericElement? {
        if (encoder::class.simpleName?.startsWith("Yaml") != true) return null
        if (value !is Any) return null
        @Suppress("UNCHECKED_CAST")
        return GenericElementWrapper(value, serializer as KSerializer<Any>)
    }

    override fun tryDeserialize(decoder: Decoder): GenericElement? {
        if (decoder::class.simpleName?.startsWith("Yaml") != true) return null
        val deserializer = YamlNode.serializer()
        val yamlNode = decoder.decodeSerializableValue(deserializer)
        return yamlNode.toGenericElement()
    }

    private fun YamlNode.toGenericElement(): GenericElement {
        return when (this) {
            is YamlMap -> GenericElementMap(
                entries.map { (key, value) ->
                    key.content to value.toGenericElement()
                }.toMap()
            )
            is YamlList -> GenericElementList(items.map { it.toGenericElement() })
            is YamlScalar -> GenericElementString(content)
            else -> GenericElement.EmptyObject
        }
    }
}
