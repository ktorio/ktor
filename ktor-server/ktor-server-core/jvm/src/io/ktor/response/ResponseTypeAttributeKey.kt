/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.response

import io.ktor.util.*
import kotlin.reflect.*

@InternalAPI
public val ResponseTypeAttributeKey: AttributeKey<KType> = AttributeKey("ResponseTypeAttributeKey")
