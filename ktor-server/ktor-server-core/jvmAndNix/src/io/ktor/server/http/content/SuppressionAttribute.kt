/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.util.*

/**
 * Attribute that could be added to an application call to prevent it's response from being compressed
 */
public val SuppressionAttribute: AttributeKey<Boolean> = AttributeKey("preventCompression")
