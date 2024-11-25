/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*

fun <T> NamedDomainObjectContainer<T>.maybeRegister(name: String, configure: T.() -> Unit): NamedDomainObjectProvider<T> {
    return if (name in names) named(name, configure) else register(name, configure)
}

internal fun <T> NamedDomainObjectCollection<T>.maybeNamed(name: String): NamedDomainObjectProvider<T>? {
    return if (name in names) named(name) else null
}

internal fun <T> NamedDomainObjectCollection<T>.maybeNamed(name: String, configure: T.() -> Unit) {
    if (name in names) named(name).configure(configure)
}

internal inline fun <reified T> NamedDomainObjectCollection<*>.findByName(name: String): T? = findByName(name) as? T
