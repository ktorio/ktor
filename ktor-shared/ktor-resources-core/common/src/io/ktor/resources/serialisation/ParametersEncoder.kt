/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources.serialisation

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*

@OptIn(ExperimentalSerializationApi::class)
internal class ParametersEncoder(
    override val serializersModule: SerializersModule
) : AbstractEncoder() {

    private val parametersBuilder = ParametersBuilder()

    val parameters: Parameters
        get() = parametersBuilder.build()

    private lateinit var nextElementName: String

    override fun encodeValue(value: Any) {
        parametersBuilder.append(nextElementName, value.toString())
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        if (descriptor.kind != StructureKind.LIST) {
            nextElementName = descriptor.getElementName(index)
        }
        return true
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        encodeValue(enumDescriptor.getElementName(index))
    }

    override fun encodeNull() {
        // no op
    }
}
