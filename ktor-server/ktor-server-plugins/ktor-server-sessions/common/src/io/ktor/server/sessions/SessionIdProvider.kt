/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import io.ktor.util.*

/**
 * Generates a secure random session ID
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.generateSessionId)
 */
public fun generateSessionId(): String = generateNonce() + generateNonce()
