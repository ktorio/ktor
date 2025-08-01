/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.annotations

/**
 * Introduces a `name` qualifier for differentiating classpath references in the
 * dependency injection plugin.
 *
 * This is used for the declaration when applied to a type for function, or to the
 * resolution when applied to a parameter.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.annotations.Named)
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.TYPE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class Named(val value: String = "")
