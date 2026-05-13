/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.lang.reflect.*
import java.net.*

/**
 * Returns `true` if this classloader is compatible with Ktor's auto-reload mechanism.
 *
 * Auto-reload uses [OverridingClassLoader], which extends [URLClassLoader] and therefore requires
 * a standard URL-based classpath. Non-standard classloaders — such as Spring Boot's
 * `LaunchedClassLoader` or Amper's fat-JAR launcher — use custom URL schemes (e.g. `jar:nested:/`)
 * that [URLClassLoader] cannot handle, leading to duplicate class identities and [LinkageError].
 *
 * JDK's built-in `AppClassLoader` is not a [URLClassLoader] since JDK 11 but is still compatible.
 */
internal fun ClassLoader.supportsAutoReload(): Boolean =
    this is URLClassLoader ||
        javaClass.name == "jdk.internal.loader.ClassLoaders\$AppClassLoader"

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
 * reorganisation in JDK9+. However, if failed, it fallbacks to [urlClassPathByPackagesList] implementation
 * that should always work.
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
    } catch (_: Throwable) {
        return try {
            urlClassPathByPackagesList()
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Extract classloader's packages list and guess URLs by package segments.
 * Unlike the old way, this doesn't require any black magic so works well on all JDKs
 * from JDK6 to the latest.
 */
private fun ClassLoader.urlClassPathByPackagesList(): List<URL> {
    val allPackagePaths = ClassLoaderDelegate(this).packagesList().map { it.replace('.', '/') }
        .flatMapTo(HashSet<String>()) { packageName ->
            val segments = packageName.split('/')
            (1..segments.size).map { segments.subList(0, it).joinToString("/") } + packageName
        }.sortedBy { it.count { character -> character == '/' } } + ""

    return allPackagePaths.flatMap { path -> getResources(path)?.toList() ?: emptyList() }
        .distinctBy { it.path.substringBefore('!') }
}

private fun Class<*>.findURLClassPathField(): Field? {
    declaredFields.firstOrNull { it.name == "ucp" && it.type.simpleName == "URLClassPath" }?.let { return it }
    return superclass?.findURLClassPathField() ?: return null
}

/**
 * This is auxillary classloader that is not used for loading classes. The purpose is just
 * to get access to [getPackages] function that is unfortunately protected.
 */
private class ClassLoaderDelegate(delegate: ClassLoader) : ClassLoader(delegate) {
    fun packagesList(): List<String> = getPackages().map { it.name }
}
