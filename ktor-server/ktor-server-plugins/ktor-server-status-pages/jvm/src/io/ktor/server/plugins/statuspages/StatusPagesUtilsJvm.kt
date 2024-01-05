/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.statuspages

import kotlin.reflect.*

internal actual fun selectNearestParentClass(cause: Throwable, keys: List<KClass<*>>): KClass<*>? =
    keys.minByOrNull { distance(cause.javaClass, it.java) }

private fun distance(child: Class<*>, parent: Class<*>): Int {
    var result = 0
    var current = child
    while (current != parent) {
        current = current.superclass
        result++
    }

    return result
}
