/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.annotations

/**
 * Marks open types that are not intended to be subclassed outside Ktor and its plugins.
 *
 * Subclassing requires explicit opt-in via [OptIn].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.annotations.InternalKtorSubclassing)
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This type is not intended to be subclassed outside Ktor and its plugins.",
)
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class InternalKtorSubclassing
