/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.plugins.compression

import io.ktor.client.statement.HttpResponse

/**
 * Darwin handles content encoding automatically.
 */
internal actual fun shouldDecode(response: HttpResponse): Boolean = false
