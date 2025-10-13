/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.Serializable

@Serializable
public data class ExampleObject(
    public val summary: String? = null,
    public val description: String? = null,
    public val value: GenericElement? = null,
    public val externalValue: String? = null,
)
