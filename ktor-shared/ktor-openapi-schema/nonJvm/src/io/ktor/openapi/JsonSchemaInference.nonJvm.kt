/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.internal.GeneratedSerializer

internal actual fun typeName(mapping: JsonSchema.Discriminator.Mapping): String? =
    mapping.ref.simpleName

internal actual fun sealedSubclassComponentNameMapping(serializer: KSerializer<*>?): Map<String, String> =
    emptyMap()

@OptIn(InternalSerializationApi::class)
internal actual fun nestedSerializerAt(serializer: KSerializer<*>?, index: Int): KSerializer<*>? =
    (serializer as? GeneratedSerializer<*>)?.childSerializers()?.getOrNull(index)

internal actual fun subclassComponentName(serializer: KSerializer<*>?): String? = null
