/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/** Meta-annotation making an annotation inherited by subclasses on the JVM, and doing nothing elsewhere. */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class JvmInherited()
