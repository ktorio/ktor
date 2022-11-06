package io.ktor.server.auth

import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.response.respond

public class BearerAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    private val schemes: List<String> = config.schemes
    private val authenticate = config.authenticate
    private val getAuthHeader: (ApplicationCall) -> HttpAuthHeader? = config.getAuthHeader

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val authHeader = getAuthHeader(context.call) ?: let {
            context.challenge(challengeKey, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(UnauthorizedResponse())
                challenge.complete()
            }
            return
        }

        val principal = (authHeader as? HttpAuthHeader.Single)
            ?.takeIf { it.authScheme.lowercase() in schemes }
            ?.let { authenticate(context.call, BearerTokenCredential(it.blob)) }
            ?: let {
                context.challenge(challengeKey, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    call.respond(UnauthorizedResponse())
                    challenge.complete()
                }
                return
            }

        context.principal(principal)
    }

    public class Config(name: String?) : AuthenticationProvider.Config(name) {
        internal var authenticate: AuthenticationFunction<BearerTokenCredential> = {
            throw NotImplementedError(
                "Bearer auth authenticate function is not specified. Use bearer { authenticate { ... } } to fix."
            )
        }

        internal var getAuthHeader: (ApplicationCall) -> HttpAuthHeader? = { call ->
            call.request.parseAuthorizationHeader()
        }

        internal var schemes = listOf("bearer")

        /**
         * Exchanges the token for a Principal.
         * @return a principal or `null`
         */
        public fun authenticate(authenticate: suspend ApplicationCall.(BearerTokenCredential) -> Principal?) {
            this.authenticate = authenticate
        }

        /**
         * Retrieves an HTTP authentication header.
         * By default, it parses the `Authorization` header content.
         */
        public fun authHeader(getAuthHeader: (ApplicationCall) -> HttpAuthHeader?) {
            this.getAuthHeader = getAuthHeader
        }

        /**
         * Provide the auth schemes accepted when validating the authentication.
         * By default, it accepts "Bearer" scheme.
         */
        public fun authSchemes(vararg schemes: String) {
            this.schemes = schemes.map { it.lowercase() }
        }

        internal fun build() = BearerAuthenticationProvider(this)
    }
}

public fun AuthenticationConfig.bearer(
    name: String? = null,
    configure: BearerAuthenticationProvider.Config.() -> Unit,
) {
    val provider = BearerAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

private val challengeKey: Any = "BearerAuth"
