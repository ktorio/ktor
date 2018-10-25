package io.ktor.auth

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import io.ktor.routing.*
import io.ktor.util.*
import org.slf4j.*

/**
 * Authentication feature supports pluggable mechanisms for checking and challenging a client to provide credentials
 *
 * @param config initial authentication configuration
 */
class Authentication(config: Configuration) {
    /**
     * @param providers list of registered instances of [AuthenticationProvider]
     */
    constructor(providers: List<AuthenticationProvider>) : this(Configuration(providers))

    constructor() : this(Configuration())

    private var config = config.copy()

    private val logger = LoggerFactory.getLogger(Authentication::class.java)

    /**
     * Authentication configuration
     */
    class Configuration(providers: List<AuthenticationProvider> = emptyList()) {
        internal val providers = ArrayList<AuthenticationProvider>(providers)

        /**
         * Register a provider with the specified [name] and [configure] it
         * @throws IllegalArgumentException if there is already provider installed with the same name
         */
        fun provider(name: String? = null, configure: AuthenticationProvider.() -> Unit) {
            if (providers.any { it.name == name })
                throw IllegalArgumentException("Provider with the name $name is already registered")
            val configuration = AuthenticationProvider(name).apply(configure)
            providers.add(configuration)
        }

        /**
         * Register the specified [provider]
         * @throws IllegalArgumentException if there is already provider installed with the same name
         */
        fun register(provider: AuthenticationProvider) {
            if (providers.any { it.name == provider.name })
                throw IllegalArgumentException("Provider with the name ${provider.name} is already registered")
            providers.add(provider)
        }

        internal fun copy(): Configuration = Configuration(providers)
    }

    init {
        config.providers.forEach { forEveryProvider(it.pipeline) }
    }

    /**
     * Configure already installed feature
     */
    fun configure(block: Configuration.() -> Unit) {
        val newConfiguration = config.copy()
        block(newConfiguration)
        val added = newConfiguration.providers - config.providers

        config = newConfiguration.copy()
        added.forEach { forEveryProvider(it.pipeline) }
    }

    /**
     * Configures [pipeline] to process authentication by one or multiple auth methods
     * @param pipeline to be configured
     * @param configurationNames references to auth providers, could contain null to point to default
     */
    fun interceptPipeline(
        pipeline: ApplicationCallPipeline,
        configurationNames: List<String?> = listOf(null),
        optional: Boolean = false
    ) {
        require(configurationNames.isNotEmpty()) { "At least one configuration name or default listOf(null)" }

        val configurations = configurationNames.map { configurationName ->
            config.providers.firstOrNull { it.name == configurationName }
                ?: throw IllegalArgumentException(
                    if (configurationName == null)
                        "Default authentication configuration was not found"
                    else
                        "Authentication configuration with the name $configurationName was not found"
                )
        }

        val authenticationPipeline = when {
            configurations.size == 1 -> configurations[0].pipeline
            else -> AuthenticationPipeline().apply {
                for (provider in configurations) {
                    merge(provider.pipeline)
                }
            }
        }

        pipeline.insertPhaseAfter(ApplicationCallPipeline.Features, authenticationPhase)
        pipeline.insertPhaseAfter(authenticationPhase, challengePhase)

        pipeline.intercept(authenticationPhase) {
            val call = call
            val authenticationContext = AuthenticationContext.from(call)
            if (authenticationContext.principal != null) return@intercept

            processAuthentication(call, authenticationContext, configurations, authenticationPipeline)
        }

        pipeline.intercept(challengePhase) {
            val context = AuthenticationContext.from(call)

            when {
                context.principal != null -> {
                }
                context.challenge.completed -> finish()
                else -> {
                    if (!optional) {
                        executeChallenges(context, true)
                    }
                }
            }
        }
    }

    /**
     * Installable feature for [Authentication].
     */
    companion object Feature : ApplicationFeature<Application, Configuration, Authentication> {
        private val authenticationPhase = PipelinePhase("Authenticate")
        private val challengePhase = PipelinePhase("Challenge")

        override val key = AttributeKey<Authentication>("Authentication")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): Authentication {
            return Authentication().apply {
                configure(configure)
            }
        }
    }

    private fun forEveryProvider(authenticationPipeline: AuthenticationPipeline) {
        // Install challenging interceptor for every provider
        authenticationPipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            val principal = context.principal
            if (principal != null) {
                finish()
                return@intercept
            }

            if (context.challenge.register.all { it.first === AuthenticationFailedCause.NoCredentials }) {
                return@intercept
            }

            // NOTE: we don't handle errors per-provider here, we do it in the end
            executeChallenges(context, false)
        }
    }

    private suspend fun PipelineContext<*, ApplicationCall>.executeChallenges(
        context: AuthenticationContext,
        handleErrors: Boolean
    ) {
        val challengePipeline = Pipeline<AuthenticationProcedureChallenge, ApplicationCall>(challengePhase)
        val challenges = context.challenge.challenges

        for (challenge in challenges) {
            challengePipeline.intercept(challengePhase) {
                challenge(it)
                if (it.completed)
                    finish() // finish challenge pipeline if it has been completed
            }
        }

        if (handleErrors) {
            for (challenge in context.challenge.errorChallenges) {
                challengePipeline.intercept(challengePhase) {
                    challenge(it)
                    if (it.completed)
                        finish() // finish challenge pipeline if it has been completed
                }
            }

            for (error in context.errors.values.filterIsInstance<AuthenticationFailedCause.Error>()) {
                challengePipeline.intercept(challengePhase) {
                    if (!it.completed) {
                        logger.trace("Responding unauthorized because of error ${error.cause}")
                        call.respond(UnauthorizedResponse())
                        it.complete()
                        finish()
                    }
                }
            }
        }

        val challenge = challengePipeline.execute(call, context.challenge)
        if (challenge.completed)
            finish() // finish authentication pipeline if challenge has been completed
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.processAuthentication(
        call: ApplicationCall,
        authenticationContext: AuthenticationContext,
        configurations: List<AuthenticationProvider>,
        defaultPipeline: AuthenticationPipeline
    ) {
        val firstSkippedIndex = configurations.indexOfFirst {
            it.skipWhen.any { skipCondition -> skipCondition(call) }
        }

        if (firstSkippedIndex == -1) { // all configurations are unskipped - apply the whole prebuilt pipeline
            defaultPipeline.execute(call, authenticationContext)
        } else if (firstSkippedIndex == 0 && configurations.size == 1) {
            return // the only configuration is skipped - fast-path return
        } else {
            // rebuild pipeline to skip particular auth methods

            val child = AuthenticationPipeline()
            var applicableCount = 0

            for (idx in 0 until firstSkippedIndex) {
                child.merge(configurations[idx].pipeline)
                applicableCount++
            }

            for (idx in firstSkippedIndex + 1 until configurations.size) {
                val provider = configurations[idx]
                if (provider.skipWhen.none { skipCondition -> skipCondition(call) }) {
                    child.merge(provider.pipeline)
                    applicableCount++
                }
            }

            if (applicableCount > 0) {
                child.execute(call, authenticationContext)
            }
        }

        if (authenticationContext.challenge.completed) {
            finish() // finish call pipeline if authentication challenge has been completed
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

/**
 * Creates an authentication route that does handle authentication by the specified providers referred by
 * [configurations] names. `null` could be used to point to the default provider and could be also mixed with other
 * provider names.
 * Other routes, handlers and interceptors could be nested into this node
 *
 * The [Authentication] feature need to be installed first otherwise
 * it will fail with [MissingApplicationFeatureException] and all providers requested by [configurations] need
 * to be already registered.
 *
 * Is is important to note that when [optional] is set, challenges will be skipped only if no credentials are provided.
 *
 * To summarize:
 *
 * optional=false:
 *   - No credentials: challenge is sent and route is NOT executed
 *   - Bad credentials: Unauthorized
 *   - Good credentials: route handler will be executed
 *
 * optional=true:
 *   - No credentials: route handler will be executed with a null [Principal]
 *   - Bad credentials: Unauthorized
 *   - Good credentials: route handler will be executed
 *
 * @param configurations names that point to already registered authentication providers
 * @param optional when set, if no auth is provided by the client, the call will continue but with a null [Principal]
 * @throws MissingApplicationFeatureException if no [Authentication] feature installed first
 * @throws IllegalArgumentException if there are no registered providers referred by [configurations] names
 */
fun Route.authenticate(
    vararg configurations: String? = arrayOf<String?>(null),
    optional: Boolean = false,
    build: Route.() -> Unit
): Route {
    require(configurations.isNotEmpty()) { "At least one configuration name or null for default need to be provided" }
    val configurationNames = configurations.distinct()
    val authenticatedRoute = createChild(AuthenticationRouteSelector(configurationNames))

    application.feature(Authentication).interceptPipeline(authenticatedRoute, configurationNames, optional = optional)
    authenticatedRoute.build()
    return authenticatedRoute
}

/**
 * An authentication route node that is used by [Authentication] feature
 * and usually created by [Route.authenticate] DSL function so generally there is no need to instantiate it directly
 * unless you are writing an extension
 * @param names of authentication providers to be applied to this route
 */
class AuthenticationRouteSelector(val names: List<String?>) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Constant
    }

    override fun toString(): String = "(authenticate ${names.joinToString { it ?: "\"default\"" }})"
}

/**
 * Installs [Authentication] feature if not yet installed and invokes [block] on it's config.
 * One is allowed to modify existing authentication configuration only in [authentication]'s block or
 * via [Authentication.configure] function.
 * Changing captured instance of configuration outside of [block] may have no effect or damage application's state.
 */
fun Application.authentication(block: Authentication.Configuration.() -> Unit) {
    featureOrNull(Authentication)?.configure(block) ?: install(Authentication, block)
}
