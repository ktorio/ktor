/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*

internal fun <T> NamedDomainObjectCollection<T>.maybeNamed(name: String): NamedDomainObjectProvider<T>? {
    return if (name in names) named(name) else null
}

internal inline fun <reified T> NamedDomainObjectCollection<*>.findByName(name: String): T? = findByName(name) as? T
