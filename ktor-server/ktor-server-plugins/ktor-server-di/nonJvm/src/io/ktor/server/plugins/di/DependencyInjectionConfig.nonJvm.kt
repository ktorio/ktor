/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

/**
 * Reflection is currently disabled for all non-JVM platforms.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DefaultReflection)
 */
public actual val DefaultReflection: DependencyReflection
    get() = NoReflection
