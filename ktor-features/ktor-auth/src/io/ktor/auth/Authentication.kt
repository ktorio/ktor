package io.ktor.auth

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.routing.*
import io.ktor.util.*

/**
 * Authentication feature supports pluggable mechanisms for checking and challenging a client to provide credentials
 *
 * @param providers list of registered instances of [AuthenticationProvider]
 */
class Authentication(val providers: List<AuthenticationProvider>) {
    private val challengePhase = PipelinePhase("Challenge")

    class Configuration {
        val providers = ArrayList<AuthenticationProvider>()

        fun provider(name: String? = null, configure: AuthenticationProvider.() -> Unit) {
            if (providers.any { it.name == name })
                throw IllegalArgumentException("Provider with the name $name is already registered")
            val configuration = AuthenticationProvider(name).apply(configure)
            providers.add(configuration)
        }

        fun register(provider: AuthenticationProvider) {
            if (providers.any { it.name == provider.name })
                throw IllegalArgumentException("Provider with the name ${provider.name} is already registered")
            providers.add(provider)
        }
    }

    init {
        providers.forEach { forEveryProvider(it.pipeline) }
    }

    private fun forEveryProvider(authenticationPipeline: AuthenticationPipeline) {
        authenticationPipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            val principal = context.principal
            if (principal != null) {
                finish()
                return@intercept
            }

            if (context.challenge.register.all { it.first === AuthenticationFailedCause.NoCredentials }) {
                return@intercept
            }

            executeChallenges()
        }
    }

    private fun ensureChallenges(authenticationPipeline: AuthenticationPipeline) {
        // Install challenging interceptor, it installs last
        authenticationPipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            if (context.principal == null && context.challenge.completed) {
                finish()
                return@intercept
            }

            executeChallenges()
        }
    }

    private suspend fun PipelineContext<AuthenticationContext, ApplicationCall>.executeChallenges() {
        val context = subject
        val challengePipeline = Pipeline<AuthenticationProcedureChallenge, ApplicationCall>(challengePhase)
        for (challenge in context.challenge.challenges) {
            challengePipeline.intercept(challengePhase) {
                challenge(it)
                if (it.completed)
                    finish() // finish challenge pipeline if it has been completed
            }
        }

        val challenge = challengePipeline.execute(call, context.challenge)
        if (challenge.completed)
            finish() // finish authentication pipeline if challenge has been completed
    }

    fun interceptPipeline(pipeline: ApplicationCallPipeline, configurationNames: List<String?> = listOf(null)) {
        require(configurationNames.isNotEmpty()) { "It should be at least one configuration name or default listOf(null)" }

        val configurations = configurationNames.map { configurationName ->
            providers.firstOrNull { it.name == configurationName }
                ?: throw IllegalArgumentException(
                    if (configurationName == null)
                        "Default authentication configuration was not found"
                    else
                        "Authentication configuration with the name $configurationName was not found"
                )
        }

        val merged = AuthenticationPipeline()

        for (cfg in configurations) {
            merged.merge(cfg.pipeline)
        }

        ensureChallenges(merged)

        pipeline.insertPhaseAfter(ApplicationCallPipeline.Infrastructure, authenticationPhase)
        pipeline.intercept(authenticationPhase) {
            val call = call
            val authenticationContext = AuthenticationContext.from(call)

            if (authenticationContext.principal != null) return@intercept

            val firstSkippedIndex = configurations.indexOfFirst {
                it.skipWhen.any { skipCondition -> skipCondition(call) }
            }

            if (firstSkippedIndex == -1) { // all configurations are unskipped - apply the whole prebuilt pipeline
                merged.execute(call, authenticationContext)
            } else {
                // rebuild pipeline to skip particular auth methods

                val child = AuthenticationPipeline()
                var qty = 0
                for (idx in 0 until firstSkippedIndex) {
                    child.merge(configurations[idx].pipeline)
                    qty++
                }
                for (idx in firstSkippedIndex + 1 until configurations.size) {
                    val cfg = configurations[idx]
                    if (cfg.skipWhen.none { skipCondition -> skipCondition(call) }) {
                        child.merge(cfg.pipeline)
                        qty++
                    }
                }

                if (qty == 0) { // all auth methods are skipped so simply leave
                    return@intercept
                }

                ensureChallenges(child)

                child.execute(call, authenticationContext)
            }

            if (authenticationContext.challenge.completed) {
                finish() // finish call pipeline if authentication challenge has been completed
            }
        }
    }

    /**
     * Installable feature for [Authentication].
     */
    companion object Feature : ApplicationFeature<Application, Configuration, Authentication> {
        private val authenticationPhase = PipelinePhase("Authenticate")

        override val key = AttributeKey<Authentication>("Authentication")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): Authentication {
            val configuration = Configuration().apply(configure)
            return Authentication(configuration.providers)
        }
    }
}

/**
 * Retrieves an [AuthenticationContext] for `this` call
 */
val ApplicationCall.authentication: AuthenticationContext
    get() = AuthenticationContext.from(this)

/**
 * Retrieves authenticated [Principal] for `this` call
 */
inline fun <reified P : Principal> ApplicationCall.principal() = authentication.principal<P>()

fun Route.authenticate(vararg configurations: String? = arrayOf<String?>(null), build: Route.() -> Unit): Route {
    val configurationNames = configurations.distinct()
    val authenticatedRoute = createChild(AuthenticationRouteSelector(configurationNames))

    application.feature(Authentication).interceptPipeline(authenticatedRoute, configurationNames)
    authenticatedRoute.build()
    return authenticatedRoute
}

class AuthenticationRouteSelector(val names: List<String?>) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Constant
    }

    override fun toString(): String = "(authenticate ${names.joinToString { it ?: "\"default\"" }})"
}

