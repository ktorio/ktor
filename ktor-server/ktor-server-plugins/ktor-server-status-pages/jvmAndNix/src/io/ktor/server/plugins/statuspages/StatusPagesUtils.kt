/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.statuspages

import kotlin.reflect.*

internal expect fun selectNearestParentClass(cause: Throwable, keys: List<KClass<*>>): KClass<*>?
