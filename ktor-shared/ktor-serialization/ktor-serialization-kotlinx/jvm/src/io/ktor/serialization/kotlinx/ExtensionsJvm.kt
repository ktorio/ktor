/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.util.reflect.*
import io.ktor.utils.io.*

@OptIn(InternalAPI::class)
internal actual val providers: List<KotlinxSerializationExtensionProvider> =
    loadServices<KotlinxSerializationExtensionProvider>()
