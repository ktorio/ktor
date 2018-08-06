package io.ktor.ratelimits

import io.ktor.application.ApplicationCall
import io.ktor.features.origin
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import java.util.concurrent.ConcurrentHashMap

/**
 * A controller for [RateLimit] instances.
 *
 * This exists solely for library users to implement their
 * own provision/storage/expiration of [RateLimit] data.
 *
 * Additionally this may be used to control the response to a
 * rate limit being exceeded, as well as the key generation
 * for the rate limits.
 */
interface RateLimitController {
    /**
     * Retrieves a [RateLimit] representing the [call]
     * with the provided [key], or `null` if a new one
     * should be generated.
     *
     * @param call The incoming [ApplicationCall].
     * @param key The key representing the RateLimit of the [call].
     */
    fun retrieve(call: ApplicationCall, key: String): RateLimit?

    /**
     * Stores the [rateLimit] provided using the specified [key].
     *
     * A [call] is provided if storage requires additional information
     * not included in the [key], and/or functionality of the call itself.
     *
     * @param call The incoming [ApplicationCall].
     * @param key The key representing the RateLimit of the [call].
     * @param rateLimit The [RateLimit] to store.
     */
    fun store(call: ApplicationCall, key: String, rateLimit: RateLimit)

    /**
     * Produces a key to identify the incoming [call]'s [RateLimit].
     *
     * This key should be unique in order to correctly represent the client or the call
     * itself, but may also be unique to other factors such as the distinct URI requested.
     * The key returned must also be produced procedurally from information contained
     * in the [call], and not pseudo-randomly or with mutable properties, so it may be
     * reproduced accurately more than once per call by functions such as
     * [ApplicationCall.rateLimit].
     *
     * By default this returns a key-value string in the format:
     * ```
     * [address=X, path=Y]
     * ```
     *
     * Where `X` is the [remote host address][io.ktor.http.RequestConnectionPoint.remoteHost]
     * and `Y` is the [path uri][io.ktor.http.RequestConnectionPoint.uri] without the query
     * if one is present.
     *
     * @param call The incoming [ApplicationCall] to get the key identifier for.
     *
     * @return A key identifier for the [call].
     */
    fun produceKey(call: ApplicationCall): String {
        val request = call.request
        val address = request.local.remoteHost
        val path = request.origin.uri.substringBefore('?')
        return "[address=$address, path=$path]"
    }

    /**
     * Handles a request where the [rateLimit] has been
     * [exceeded][RateLimit.exceeded].
     *
     * Implementations should override this function in order to
     * implement a general and unified response behavior to
     * an exceeded rate-limit.
     */
    suspend fun onExceed(call: ApplicationCall, rateLimit: RateLimit) {
        call.respond(HttpStatusCode.TooManyRequests)
    }
}

// Simple concrete implementation storing rate-limits internally in a map
internal class MapRateLimitController: RateLimitController {
    private val rateLimits = ConcurrentHashMap<String, RateLimit>()

    override fun retrieve(call: ApplicationCall, key: String): RateLimit? {
        return rateLimits[key]
    }

    override fun store(call: ApplicationCall, key: String, rateLimit: RateLimit) {
        rateLimits[key] = rateLimit
    }
}