/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.annotations

@Retention(AnnotationRetention.RUNTIME)
public annotation class Named(val value: String = "")
