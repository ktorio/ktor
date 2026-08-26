/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/**
 * Meta-annotation making an annotation inherited by subclasses on the JVM, and doing nothing
 * elsewhere.
 *
 * On the JVM this is `java.lang.annotation.Inherited`, which is what JUnit's annotation lookup
 * consults before walking up a superclass chain (see `AnnotationSupport.findAnnotation`). A Kotlin
 * annotation declared in common code cannot apply the Java annotation directly, so it goes through
 * this alias; every other target has no such concept and gets an annotation with no effect.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class JvmInherited()
