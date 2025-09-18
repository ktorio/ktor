/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.i18n

import io.ktor.server.routing.RoutingContext
import org.jetbrains.annotations.PropertyKey

/**
 * Translate a message key to an accepted language specified in HTTP request
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.i18n.i18n)
 */
public fun RoutingContext.i18n(@PropertyKey(resourceBundle = BUNDLE_KEY) key: String): String {
    return call.i18n(key)
}
