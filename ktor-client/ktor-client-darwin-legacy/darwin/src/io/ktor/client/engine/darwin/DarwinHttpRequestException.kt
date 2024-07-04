/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.call.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import platform.Foundation.*
import platform.posix.*

@Suppress("KDocMissingDocumentation")
public class DarwinHttpRequestException(public val origin: NSError) : IOException("Exception in http request: $origin")
