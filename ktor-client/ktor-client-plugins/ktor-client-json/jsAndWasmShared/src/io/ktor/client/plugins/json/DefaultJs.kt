/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.plugins.json

import io.ktor.utils.io.*

/**
 * Platform default serializer.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.json.defaultSerializer)
 */
@OptIn(InternalAPI::class)
public actual fun defaultSerializer(): JsonSerializer =
    serializersStore.first()

@InternalAPI
public val serializersStore: MutableList<JsonSerializer> = mutableListOf()
