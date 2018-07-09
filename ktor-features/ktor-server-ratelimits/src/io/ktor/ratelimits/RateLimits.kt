@file:Suppress("MemberVisibilityCanBePrivate")
package io.ktor.ratelimits

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelinePhase
import io.ktor.response.header
import io.ktor.routing.Route
import io.ktor.routing.RouteSelector
import io.ktor.routing.RouteSelectorEvaluation
import io.ktor.routing.RoutingResolveContext
import io.ktor.util.AttributeKey
import java.time.Duration
import java.time.Instant

/**
 * Installable feature for ktor server applications.
 *
 * Installation of this feature allows routes to automatically
 * respond to incoming requests that exceed specified rate limits.
 *
 * ```kotlin
 * install(RateLimits)
 *
 * routing {
 *     rateLimit("/hello", limit = 5, seconds = 5) {
 *         get {
 *             call.response.status(HttpStatusCode.OK)
 *             call.respond("""{"message":"Hello, World!"}""")
 *         }
 *     }
 * }
 * ```
 */
class RateLimits private constructor(configuration: Configuration) {
    internal val controller = configuration.controller

    /**
     * The [RateLimitPipeline] for this feature.
     */
    val pipeline = configuration.pipeline

    init {
        // Intercept and store rate limit
        pipeline.intercept(RateLimitPipeline.StoreRateLimit) {
            val rateLimit = subject
            // store rate limit in controller and in attributes for future use
            controller.store(call, rateLimit.key, rateLimit)
            call.attributes.put(rateLimitKey, rateLimit)
        }

        // Intercept and apply the rate limit headers
        val (limit, remaining, reset) = configuration.xRateLimit
        pipeline.intercept(RateLimitPipeline.AppendRateLimitHeaders) {
            val rateLimit = subject
            with(call.response) {
                // Retry-After Header
                val rateLimitDuration = Duration.between(call.attributes.take(instantKey), rateLimit.reset)
                header(HttpHeaders.RetryAfter, "${rateLimitDuration.toMillis()}")

                // X-RateLimit-___ headers
                limit?.let { header(it, rateLimit.limit) }
                remaining?.let { header(it, rateLimit.remaining) }
                reset?.let {
                    val instant = rateLimit.reset as? Instant ?: Instant.from(rateLimit.reset)
                    header(it, instant.toEpochMilli())
                }
            }

            // If we haven't exceeded this rate-limit, the pipeline execution should
            if(!rateLimit.exceeded) finish()
        }

        // Intercept when the rate limit exceeds in above phase
        pipeline.intercept(RateLimitPipeline.OnExceededRateLimit) {
            // Set status to 429 - Too Many Requests
            call.response.status(HttpStatusCode.TooManyRequests)
            controller.onExceed(call, subject)

            // Pipeline execution should ALWAYS finish here.
            finish()
        }
    }

    internal fun interceptPipeline(parent: Route, limit: Int, reset: Duration): Route {
        require(limit > 0) { "Cannot rate limit route with a limit less than 1!" }

        checkAncestors(parent)

        // Create a child route to use as our intercepted pipeline
        val child = parent.createChild(Selector(parent, limit, reset))

        // Insert a phase before the actual call logic
        child.insertPhaseBefore(ApplicationCallPipeline.Call, ApplicationCallPipeline.RateLimit)

        // Register an interceptor on the newly inserted phase
        child.intercept(ApplicationCallPipeline.RateLimit) {
            // select a key
            val key = controller.produceKey(call)

            // get now since we're most likely going to be using it in any case.
            val now = Instant.now()
            call.attributes.put(instantKey, now)

            // first try to retrieve an existing one
            val rateLimit =
                controller.retrieve(call, key)
                    // if it's reset hasn't passed yet, we'll proceed,
                    //otherwise we'll create a new ratelimit
                    ?.takeIf { (it.reset as? Instant ?: Instant.from(it.reset)).isAfter(now) }
                    // Copy the ratelimit, incrementing it by 1 use.
                    // If we exceed the ratelimit, we also need to mark it as exceeded.
                    // Note that "exceeded" should only be true if we are already
                    //meeting the limit upon copying this RateLimit instance.
                    ?.incrementUses() ?:
                run { RateLimit(key, 1, limit, now.plus(reset)) }

            // Throw an exception if the key is different
            require(key == rateLimit.key) { "Illegal RateLimit key! Expected: $key, Actual: ${rateLimit.key}" }

            // Execute pipeline
            val pipelined = pipeline.execute(call, rateLimit)

            if(pipelined.exceeded) {
                // If the pipelined output of our rate limit has been exceeded
                //then we will finish the ApplicationCallPipeline execution
                finish()
            }
        }

        return child
    }

    /**
     * The configuration for the [RateLimits] feature.
     */
    class Configuration internal constructor() {
        internal val xRateLimit = XRateLimit()

        val pipeline = RateLimitPipeline()

        /**
         * The [RateLimitController] to use for the feature.
         *
         * By default this is an in-memory implementation that uses
         * a [MutableMap] to store [RateLimit]s.
         */
        var controller: RateLimitController = MapRateLimitController()

        /**
         * Configures [de facto RateLimit headers][XRateLimit].
         */
        fun xRateLimitHeaders(configure: XRateLimit.() -> Unit) = xRateLimit.configure()

        /**
         * Configuration for various de facto RateLimit headers.
         *
         * By default, the [RateLimits] feature does not automatically append
         * any of these common headers:
         *
         * * `X-RateLimit-Limit` - The header for the [rate limit's limit][RateLimit.limit].
         * * `X-RateLimit-Remaining` - The header for the [rate limit's remaining uses][RateLimit.remaining].
         * * `X-RateLimit-Reset` - The header for the [rate limit's reset time][RateLimit.reset].
         */
        data class XRateLimit internal constructor(
            internal var limit: String? = null,
            internal var remaining: String? = null,
            internal var reset: String? = null
        ) {
            /**
             * Sets the header for the [rate limit's limit][RateLimit.limit].
             *
             * By default, this is `X-RateLimit-Limit`.
             *
             * @param header The name of the header.
             */
            fun limit(header: String = "X-RateLimit-Limit") {
                this.limit = header
            }

            /**
             * Sets the header for the [rate limit's remaining uses][RateLimit.remaining].
             *
             * By default, this is `X-RateLimit-Remaining`.
             *
             * @param header The name of the header.
             */
            fun remaining(header: String = "X-RateLimit-Remaining") {
                this.remaining = header
            }

            /**
             * Sets the header for the [rate limit's reset time][RateLimit.reset].
             *
             * By default, this is `X-RateLimit-Reset`.
             *
             * @param header The name of the header.
             */
            fun reset(header: String = "X-RateLimit-Reset") {
                this.reset = header
            }
        }
    }

    companion object Feature: ApplicationFeature<Application, Configuration, RateLimits> {
        internal val ApplicationCallPhase = PipelinePhase("RateLimit")

        internal val instantKey = AttributeKey<Instant>("RateLimit Instant")
        internal val rateLimitKey = AttributeKey<RateLimit>("RateLimit Instance")

        override val key = AttributeKey<RateLimits>("RateLimits")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit) =
            RateLimits(Configuration().apply(configure))

        // Make sure we're not nesting inside a rate limited route already
        private fun checkAncestors(parent: Route) {
            var ancestor: Route? = parent
            while(ancestor !== null) {
                require(ancestor.selector !is Selector) { "Illegal nesting of rate limited routes!" }
                ancestor = ancestor.parent
            }
        }
    }

    private data class Selector(val route: Route, val limit: Int, val reset: Duration):
        RouteSelector(RouteSelectorEvaluation.qualityConstant) {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
            return RouteSelectorEvaluation.Constant
        }
    }
}