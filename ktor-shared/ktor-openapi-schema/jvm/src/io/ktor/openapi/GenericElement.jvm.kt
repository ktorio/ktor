/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

internal actual val genericElementSerialAdapters: List<GenericElementSerialAdapter> =
    listOf(
        GenericElementEncodingAdapter,
        JsonElementSerialAdapter,
        YamlNodeSerialAdapter,
    )
