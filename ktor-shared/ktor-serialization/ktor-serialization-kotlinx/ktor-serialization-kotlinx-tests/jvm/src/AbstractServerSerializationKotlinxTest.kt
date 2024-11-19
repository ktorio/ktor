/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test

import io.ktor.serialization.test.*
import kotlinx.serialization.*

public abstract class AbstractServerSerializationKotlinxTest : AbstractServerSerializationTest() {
    protected val serializer: KSerializer<MyEntity> = MyEntity.serializer()
    protected val listSerializer: KSerializer<List<MyEntity>> = serializer()
}
