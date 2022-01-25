/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.util.*
import io.ktor.util.reflect.*

@InternalAPI
public expect fun TypeInfo.sequenceToListTypeInfo(): TypeInfo
