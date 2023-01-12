/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import java.util.*

internal actual val providers: List<KotlinxSerializationExtensionProvider> =
    KotlinxSerializationExtensionProvider::class.java.let {
        ServiceLoader.load(it, it.classLoader).toList()
    }
