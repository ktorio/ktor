/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.lang.reflect.Field
import java.net.URL
import java.net.URLClassLoader


internal fun ClassLoader.allURLs(): Set<URL> {
    val parentUrls = parent?.allURLs() ?: emptySet()
    if (this is URLClassLoader) {
        val urls = urLs.filterNotNull().toSet()
        return urls + parentUrls
    }

    val ucp = urlClassPath() ?: return parentUrls
    return parentUrls + ucp
}

/**
 * This only works in JDK9+ with VM option `--add-opens java.base/jdk.internal.loader=ALL-UNNAMED`
 * This is required since [allURLs] function is unable to lookup url list due to modules and class loaders
 * reorganisation in JDK9+.
 */
private fun ClassLoader.urlClassPath(): List<URL>? {
    try {
        val ucpField = javaClass.findURLClassPathField() ?: return null

        ucpField.isAccessible = true
        val ucpInstance = ucpField.get(this) ?: return null

        val getURLsMethod = ucpInstance.javaClass.getMethod("getURLs") ?: return null

        getURLsMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val urls = getURLsMethod.invoke(ucpInstance) as Array<URL>?

        return urls?.toList()
    } catch (cause: Throwable) {
        return null
    }
}

private fun Class<*>.findURLClassPathField(): Field? {
    declaredFields.firstOrNull { it.name == "ucp" && it.type.simpleName == "URLClassPath" }?.let { return it }
    return superclass?.findURLClassPathField() ?: return null
}
