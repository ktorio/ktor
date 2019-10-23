/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.labels

import kotlin.reflect.*

@UseExperimental(ExperimentalStdlibApi::class)
internal actual fun KClass<*>.getName(): String? = typeOf<String>().toString()
