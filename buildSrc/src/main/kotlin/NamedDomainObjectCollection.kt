/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider

fun <T> NamedDomainObjectContainer<T>.maybeRegister(name: String, configure: T.() -> Unit): NamedDomainObjectProvider<T> {
    return if (name in names) named(name, configure) else register(name, configure)
}

internal fun <T> NamedDomainObjectCollection<T>.maybeNamed(name: String): NamedDomainObjectProvider<T>? {
    return if (name in names) named(name) else null
}

internal fun <T> NamedDomainObjectCollection<T>.maybeNamed(name: String, configure: T.() -> Unit) {
    if (name in names) named(name).configure(configure)
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
internal inline fun <reified T> NamedDomainObjectCollection<*>.findByName(name: String): T? = findByName(name) as? T
