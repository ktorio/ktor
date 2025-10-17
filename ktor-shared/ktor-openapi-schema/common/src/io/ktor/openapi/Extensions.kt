/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

/**
 * A map of arbitrary extension properties, each key starting with "x-".
 */
public typealias ExtensionProperties = Map<String, GenericElement>?
