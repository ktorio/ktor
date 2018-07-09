@file:Suppress("MemberVisibilityCanBePrivate", "Unused")
package io.ktor.ratelimits

import java.time.temporal.Temporal
import kotlin.math.min

/**
 * Data necessary to handle a rate-limited route.
 *
 * It's worth noting the [reset] property provided in the constructor should support
 * conversion to an [Instant][java.time.Instant] via [Instant.from][java.time.Instant.from],
 * as `Instant` is the implementation of [Temporal] that the [RateLimits] feature uses
 * internally.
 *
 * @param key The identifier for the call that generated this RateLimit.
 * @param uses The current number of on this RateLimit.
 * @param limit The maximum number of uses possible.
 * @param reset The time this RateLimit was last reset.
 * @param exceeded Whether or not this RateLimit has been exceeded, default `false`.
 *
 * @throws IllegalArgumentException `limit < 1`, `uses < 0`, or `uses > limit`
 */
class RateLimit(val key: String, val uses: Int, val limit: Int, val reset: Temporal, val exceeded: Boolean = false) {
    init {
        require(limit > 0) { "limit < 1" }
        require(uses >= 0) { "uses < 0" }
        require(uses <= limit) { "uses > limit" }
    }

    /**
     * The remaining uses, which is [limit] - [uses].
     */
    val remaining get() = limit - uses

    /**
     * Constructs a new RateLimit with 0 uses.
     *
     * @param key The identifier for the call that generated this RateLimit.
     * @param limit The maximum number of uses possible.
     * @param reset The time this RateLimit was last reset.
     */
    constructor(key: String, limit: Int, reset: Temporal): this(key, 0, limit, reset)

    /**
     * Creates a copy of the RateLimit with an additional use.
     *
     * If the `uses + 1` would normally exceed the [limit], the
     * copy returned will set [uses] to the value of the limit,
     * and [exceeded] will be `true`.
     *
     * @return The copy of the RateLimit.
     */
    fun incrementUses() = RateLimit(key, min(uses + 1, limit), limit, reset, uses >= limit)

    override fun toString() = toStringFormat.format(key, remaining, limit, reset.toString())
    override fun hashCode() = 31 * key.hashCode() + limit
    override fun equals(other: Any?): Boolean {
        if(other !is RateLimit) return false
        return key == other.key && limit == other.limit
    }

    private companion object {
        private const val toStringFormat = "RateLimit(key=%s, remaining=%d, limit=%d, reset=%s)"
    }
}
