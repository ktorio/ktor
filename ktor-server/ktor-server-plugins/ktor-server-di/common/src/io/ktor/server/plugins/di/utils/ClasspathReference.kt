/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.DependencyRegistry
import kotlin.jvm.JvmInline

@JvmInline
internal value class ClasspathReference private constructor(val value: String) {
    companion object {
        operator fun invoke(value: String): ClasspathReference {
            return ClasspathReference(value.replace('#', '.'))
        }
    }

    /**
     * Refers to the containing compiled type, i.e. "FileNameKt", when referring to inner classes and
     * top-level functions.  Otherwise, it may be the package name.
     */
    val container: String get() = value.lastIndexOf('.').let { index ->
        if (index == -1) return ""
        value.substring(0, index)
    }

    /**
     * The string value after the last delimiter (# or .) - generally the function name or class name.
     */
    val name: String get() = value.substringAfterLast('.')

    override fun toString(): String = value
}

internal expect fun Application.installReference(
    registry: DependencyRegistry,
    reference: ClasspathReference,
)
