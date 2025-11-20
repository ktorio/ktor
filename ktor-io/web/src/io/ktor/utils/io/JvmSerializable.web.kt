/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

@InternalAPI
public actual interface JvmSerializable

@Suppress("FunctionName")
@InternalAPI
public actual fun <T : Any> JvmSerializerReplacement(serializer: JvmSerializer<T>, value: T): Any =
    DummyJvmSimpleSerializerReplacement
