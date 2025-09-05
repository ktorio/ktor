/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

/**
 * Default reflection provider for the JVM, using standard JVM reflection calls for discovering
 * constructors and parameter details.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DefaultReflection)
 */
public actual val DefaultReflection: DependencyReflection = DependencyReflectionJvm()
