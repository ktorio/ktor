/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.util.*

public actual interface Selectable

internal actual fun Any.supportsUnixDomainSockets(): Boolean = isAFUnixSupported
