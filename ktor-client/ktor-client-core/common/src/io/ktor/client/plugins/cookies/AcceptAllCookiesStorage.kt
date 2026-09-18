/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.util.date.*

/**
 * [CookiesStorage] that stores all the cookies in an in-memory map.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.AcceptAllCookiesStorage)
 */
public class AcceptAllCookiesStorage(
    clock: () -> Long = { getTimeMillis() }
) : CookiesStorage by FilteredCookiesStorage(emptyList(), clock)
