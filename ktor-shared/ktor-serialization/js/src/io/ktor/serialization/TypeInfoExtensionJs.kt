/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.util.*
import io.ktor.util.reflect.*

@InternalAPI
public actual fun TypeInfo.sequenceToListTypeInfo(): TypeInfo {
    val listTypeInfo = typeInfo<List<Any>>()
    // trick to cast a List to a MutableList to mutate it
    (listTypeInfo.kotlinType!!.arguments as MutableList)[0] = kotlinType!!.arguments[0]
    return listTypeInfo
}
