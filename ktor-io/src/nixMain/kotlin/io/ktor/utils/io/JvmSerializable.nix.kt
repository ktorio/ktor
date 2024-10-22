/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

/** Alias for `java.io.Serializable` on JVM. Empty interface otherwise. */
public actual interface JvmSerializable

public actual fun <T : Any> JvmSerializerReplacement(serializer: JvmSerializer<T>, value: T): Any =
    DummyJvmSimpleSerializerReplacement
