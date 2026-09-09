/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.modules.EmptySerializersModule
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(InternalAPI::class, InternalSerializationApi::class, ExperimentalSerializationApi::class)
class SerializerLookupTest {

    @Test
    fun `serializer preserves nullability independent of kotlin type`() {
        val typeInfo = TypeInfo(
            type = List::class,
            isNullable = true,
            kotlinType = typeOf<List<String>>(),
        )

        val serializer = EmptySerializersModule().serializerForTypeInfo(typeInfo)

        assertTrue(serializer.descriptor.isNullable)
    }
}
