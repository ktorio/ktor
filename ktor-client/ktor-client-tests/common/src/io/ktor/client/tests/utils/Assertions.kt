/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

/**
 * Asserts that [block] completed with given type of root cause.
 */
public expect inline fun <reified T : Throwable> assertFailsAndContainsCause(block: () -> Unit)

/**
 * Asserts that a [block] fails with a specific exception of type [T] being thrown.
 */
public expect inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit)
