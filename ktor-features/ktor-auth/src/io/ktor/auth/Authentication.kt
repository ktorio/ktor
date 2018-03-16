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
        providers.forEach { configureChallenge(it.pipeline) }
    }

    fun configureChallenge(authenticationPipeline: AuthenticationPipeline) {
        // Install challenging interceptor, it installs last
        authenticationPipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            val principal = context.principal
            if (principal != null) return@intercept

            val challenges = context.challenge.challenges
            if (challenges.isEmpty()) return@intercept

            val challengePipeline = Pipeline<AuthenticationProcedureChallenge, ApplicationCall>(challengePhase)
            for (challenge in challenges) {
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
    }

    fun interceptPipeline(pipeline: ApplicationCallPipeline, configurationName: String?) {
        val configuration = providers.firstOrNull { it.name == configurationName }
                ?: throw IllegalArgumentException(
                        if (configurationName == null)
                            "Default authentication configuration was not found"
                        else
                            "Authentication configuration with the name $configurationName was not found"
                )

        pipeline.insertPhaseAfter(ApplicationCallPipeline.Infrastructure, authenticationPhase)
        pipeline.intercept(authenticationPhase) {
            // don't run authentication if any filters signals it is not needed
            if (configuration.skipWhen.any { skip -> skip(call) })
                return@intercept

            val authenticationContext = AuthenticationContext.from(call)
            configuration.pipeline.execute(call, authenticationContext)
            if (authenticationContext.challenge.completed)
                finish() // finish call pipeline if authentication challenge has been completed
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

fun Route.authenticate(configurationName: String? = null, build: Route.() -> Unit): Route {
    val authenticatedRoute = createChild(AuthenticationRouteSelector(configurationName))
    application.feature(Authentication).interceptPipeline(authenticatedRoute, configurationName)
    authenticatedRoute.build()
    return authenticatedRoute
}

class AuthenticationRouteSelector(val name: String?) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Constant
    }

    override fun toString(): String = "(authenticate ${name ?: "default"})"
}

