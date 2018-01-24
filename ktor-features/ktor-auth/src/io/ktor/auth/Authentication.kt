package io.ktor.auth

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.routing.*
import io.ktor.util.*

/**
 * Authentication feature supports pluggable mechanisms for checking and challenging a client to provide credentials
 *
 * @param authenticationPipeline keeps pipeline for this instance of a [Authentication]
 */
class Authentication(val configurations: List<AuthenticationConfiguration>) {
    private val challengePhase = PipelinePhase("Challenge")

    open class AuthenticationConfiguration(val name: String? = null) {
        val pipeline = AuthenticationPipeline()

        private var filterPredicates: MutableList<(ApplicationCall) -> Boolean>? = null

        /**
         * Authentication filters specifying if authentication is required for particular [ApplicationCall]
         *
         * If there is no filters, authentication is required. If any filter returns true, authentication is not required.
         */
        val skipWhen: List<(ApplicationCall) -> Boolean> get() = filterPredicates ?: emptyList()

        /**
         * Adds an authentication filter to the list
         */
        fun skipWhen(predicate: (ApplicationCall) -> Boolean) {
            val list = filterPredicates ?: mutableListOf()
            list.add(predicate)
            filterPredicates = list
        }
    }

    class Configuration  {
        val configurations = ArrayList<AuthenticationConfiguration>()

        fun configure(name: String? = null
                      , configure: AuthenticationConfiguration.() -> Unit) {
            val configuration = AuthenticationConfiguration(name).apply(configure)
            configurations.add(configuration)
        }
    }

    init {
        configurations.forEach { configureChallenge(it.pipeline) }
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
        val configuration = configurations.firstOrNull { it.name == configurationName }
                ?: throw IllegalArgumentException("Authentication configuration with the name $configurationName was not found")

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
            return Authentication(configuration.configurations)
        }
    }
}

/**
 * Installs authentication into `this` pipeline
 */
fun Application.authentication(configuration: Authentication.Configuration.() -> Unit): Authentication {
    return install(Authentication, configuration)
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