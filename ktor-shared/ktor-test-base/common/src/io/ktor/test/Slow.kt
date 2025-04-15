/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

/**
 * Marks tests that take a long time to execute.
 * These tests can be excluded from regular test runs using the 'excludeSlow' Gradle property.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Slow

expect fun includeSlowTests(): Boolean
