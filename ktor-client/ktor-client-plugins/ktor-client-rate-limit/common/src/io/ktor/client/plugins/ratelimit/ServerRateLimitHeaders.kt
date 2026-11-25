/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.ratelimit

import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Extracts a server cooldown from response headers without consuming the response body.
 *
 * Retry-After seconds and HTTP dates take precedence over exhausted-quota hints.
 * Supports draft RateLimit items with r/t parameters, legacy RateLimit-Remaining with relative RateLimit-Reset,
 * and X-RateLimit/X-Rate-Limit remaining/reset headers with Unix epoch resets.
 * X-RateLimit-Reset-After is relative seconds, including fractional values.
 * Response Date and Age are used when available to interpret reset times.
 *
 * @return a non-negative cooldown, or `null` when no supported valid cooldown information is present.
 */
public fun HttpResponse.rateLimitDelay(): Duration? {
    val age = headers[HttpHeaders.Age].unsignedSeconds() ?: Duration.ZERO
    val serverTime = headers[HttpHeaders.Date].httpDateMillis() ?: responseTime.timestamp
    val reference = serverTime.milliseconds + age

    headers[HttpHeaders.RetryAfter]?.trim()?.let { value ->
        value.unsignedSeconds()?.let { return it }
        value.httpDateMillis()?.let { return (it.milliseconds - reference).coerceAtLeast(Duration.ZERO) }
    }

    val delays = mutableListOf<Duration>()
    // Current IETF draft: RateLimit: "policy";r=0;t=30, "other";r=0;t=60
    for (field in headers.getAll("RateLimit").orEmpty()) {
        for (member in rateLimitMembers(field)) {
            val policy = POLICY_NAME.find(member.trimStart()) ?: continue
            val parameters = member.trimStart().substring(policy.value.length).trimStart()
            if (parameters.isNotEmpty() && !parameters.startsWith(';')) continue
            val item = parseHeaderValue("policy$parameters").singleOrNull() ?: continue
            val remaining = item.params.singleOrNull { it.name == "r" }?.value.unsignedLong()
            val reset = item.params.singleOrNull { it.name == "t" }?.value.unsignedSeconds()
            if (remaining == 0L && reset != null) delays += reset - age
        }
    }

    if (headers["RateLimit-Remaining"].unsignedLong() == 0L) {
        headers["RateLimit-Reset"].unsignedSeconds()?.let { delays += it - age }
    }

    for (prefix in listOf("X-RateLimit", "X-Rate-Limit")) {
        if (headers["$prefix-Remaining"].unsignedLong() != 0L) continue
        val relative = headers["$prefix-Reset-After"].decimalSeconds()
        val absolute = headers["$prefix-Reset"].decimalSeconds()
        when {
            relative != null -> delays += relative - age
            absolute != null -> delays += absolute - reference
        }
    }
    return delays.maxOrNull()?.coerceAtLeast(Duration.ZERO)
}

private val POLICY_NAME = Regex("^\"(?:[^\"\\\\]|\\\\.)*\"")
private val DECIMAL_SECONDS = Regex("[0-9]+(?:\\.[0-9]+)?")

// Structured field strings can contain commas and escaped quotes, including within policy names.
private fun rateLimitMembers(field: String): List<String> {
    val members = mutableListOf<String>()
    var start = 0
    var quoted = false
    var escaped = false
    for ((index, character) in field.withIndex()) {
        when {
            escaped -> escaped = false
            quoted && character == '\\' -> escaped = true
            character == '"' -> quoted = !quoted
            character == ',' && !quoted -> {
                members += field.substring(start, index)
                start = index + 1
            }
        }
    }
    if (!quoted) members += field.substring(start)
    return members
}

private fun String?.unsignedLong(): Long? {
    val value = this?.trim() ?: return null
    if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
    return value.toLongOrNull()
}

private fun String?.unsignedSeconds(): Duration? = unsignedLong()?.seconds?.takeIf { it.isFinite() }

private fun String?.decimalSeconds(): Duration? {
    val value = this?.trim() ?: return null
    if (!DECIMAL_SECONDS.matches(value)) return null
    return value.toDoubleOrNull()?.seconds?.takeIf { it.isFinite() }
}

private fun String?.httpDateMillis(): Long? {
    if (this == null) return null
    return try {
        fromHttpToGmtDate().timestamp
    } catch (_: IllegalStateException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
