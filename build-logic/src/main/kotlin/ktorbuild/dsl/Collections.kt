/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// We want these extensions to be available without importing
@file:Suppress("PackageDirectoryMismatch")

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider

fun <T> NamedDomainObjectContainer<T>.maybeRegister(name: String, configure: T.() -> Unit): NamedDomainObjectProvider<T> {
    return if (name in names) named(name, configure) else register(name, configure)
}
