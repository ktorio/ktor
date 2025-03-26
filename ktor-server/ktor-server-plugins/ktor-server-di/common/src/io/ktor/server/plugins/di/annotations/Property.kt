/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.annotations

/**
 * Used for populating parameters with configuration property values when using classpath
 * references with the dependency injection plugin.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Property(val value: String = "")
