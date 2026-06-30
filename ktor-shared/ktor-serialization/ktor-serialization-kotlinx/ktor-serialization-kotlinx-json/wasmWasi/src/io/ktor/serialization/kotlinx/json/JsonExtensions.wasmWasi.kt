/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.json

import io.ktor.serialization.kotlinx.addExtensionProvider
import io.ktor.utils.io.*

@Suppress("unused", "DEPRECATION")
@OptIn(ExperimentalStdlibApi::class, InternalAPI::class)
@EagerInitialization
private val initHook = addExtensionProvider(KotlinxSerializationJsonExtensionProvider())
