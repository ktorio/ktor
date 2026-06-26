/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.plugins.json.*
import io.ktor.client.plugins.kotlinx.serializer.*
import io.ktor.utils.io.*

@OptIn(ExperimentalStdlibApi::class)
@InternalAPI
@Suppress("unused", "DEPRECATION", "DEPRECATION_ERROR")
@EagerInitialization
private val initializer: Unit = run {
    serializersStore.add(KotlinxSerializer())
}
