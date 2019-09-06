/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

/**
 * Calculates a list of all superclasses for the given class
 */
@Suppress("unused")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun Class<*>.findAllSupertypes(): List<Class<*>> {
    error("This is no longer supported.")
}
