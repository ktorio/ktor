/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/** No target outside the JVM has a notion of annotation inheritance, so this carries no behavior. */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
actual annotation class JvmInherited actual constructor()
