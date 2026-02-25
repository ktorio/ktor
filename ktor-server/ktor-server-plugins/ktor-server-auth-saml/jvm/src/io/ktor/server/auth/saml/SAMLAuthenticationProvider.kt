/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.opensaml.saml.saml2.core.StatusCode
import org.opensaml.security.x509.BasicX509Credential
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * SAML 2.0 authentication provider for Ktor Server.
 *
 * This provider implements SP-initiated SAML 2.0 Web Browser SSO Profile.
 * It handles:
 * - Generating and signing AuthnRequests
 * - Redirecting users to the IdP for authentication
 * - Receiving and validating SAML responses
 * - Extracting user identity and attributes
 *
 * ## Security Features
 *
 * - XXE protection via secure XML parser
 * - XML Signature Wrapping (XSW) protection via SAML20AssertionValidator
 * - Replay attack protection via assertion ID cache
 * - Signature verification of assertions
 * - Timestamp validation with clock skew tolerance
 * - Audience restriction validation
 */
public class SamlAuthenticationProvider internal constructor(
    internal val config: SamlConfig
) : AuthenticationProvider(config) {

    private val logger: Logger = LoggerFactory.getLogger(SamlAuthenticationProvider::class.java)
    private val spMetadata = requireNotNull(config.sp) {
        "SP metadata must be configured. Use sp = SamlSpMetadata { ... } to set it."
    }
    private val spEntityId = requireNotNull(spMetadata.spEntityId) { "SP entity ID must be configured." }
    private val idpMetadata = requireNotNull(config.idp) {
        "IdP metadata must be configured. Use idp = parseSamlIdpMetadata(...) to set it."
    }
    private val relayValidator = config.allowedRelayStateUrls.let {
        if (it == null) {
            logger.warn("RelayState validation is disabled. This is unsafe in production.")
        }
        RelayValidator(allowedRelayStateUrls = it)
    }
    private val requestedAuthnContext = config.requestedAuthnContext

    private val authenticationFunction = requireNotNull(config.authenticationFunction) {
        "SAML auth validate function must be specified"
    }
    private val challengeFunction: SamlAuthChallengeFunction = config.challengeFunction ?: {
        call.respond(HttpStatusCode.Unauthorized)
    }

    internal val signingCredential: BasicX509Credential? = spMetadata.signingCredential

    private val signatureVerifier by lazy {
        SamlSignatureVerifier(
            idpMetadata = idpMetadata,
            allowedDigestAlgorithms = config.allowedDigestAlgorithms,
            allowedSignatureAlgorithms = config.allowedSignatureAlgorithms,
        )
    }

    private val responseProcessor: SamlResponseProcessor by lazy {
        SamlResponseProcessor(
            acsUrl = acsUrl,
            spEntityId = spEntityId,
            idpMetadata = idpMetadata,
            clockSkew = config.clockSkew,
            replayCache = config.replayCache,
            decryptionCredential = signingCredential,
            requireDestination = config.requireDestination,
            allowIdpInitiatedSso = config.allowIdpInitiatedSso,
            requireSignedResponse = config.requireSignedResponse,
            requireSignedAssertions = spMetadata.wantAssertionsSigned,
            signatureVerifier = signatureVerifier,
        )
    }

    private val acsUrl = spMetadata.acsUrl.also { url ->
        require(isAbsoluteUrl(url)) {
            "ACS URL must be an absolute URL (e.g., https://myapp.example.com/saml/acs). Got: $url"
        }
    }

    private val sloUrl = spMetadata.sloUrl.also { url ->
        if (!isAbsoluteUrl(url)) {
            logger.warn("SLO URL should be an absolute URL for proper SAML 2.0 compliance. Got: $url")
        }
    }

    private val acsPath = Url(acsUrl).encodedPath
    private val sloPath = if (isAbsoluteUrl(sloUrl)) Url(sloUrl).encodedPath else sloUrl

    private val enableSingleLogout = config.enableSingleLogout
    private val logoutProcessor: SamlLogoutProcessor? by lazy {
        if (!enableSingleLogout) return@lazy null
        SamlLogoutProcessor(
            sloUrl = sloUrl,
            idpMetadata = idpMetadata,
            clockSkew = config.clockSkew,
            replayCache = config.replayCache,
            signatureVerifier = signatureVerifier,
            requireDestination = config.requireDestination,
            requireSignedLogoutRequest = spMetadata.wantAssertionsSigned,
            requireSignedLogoutResponse = config.requireSignedResponse,
        )
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val request = context.call.request
        when {
            request.httpMethod == HttpMethod.Post && request.path() == acsPath ->
                context.handleSamlCallback()

            enableSingleLogout && request.path() == sloPath -> context.handleSloEndpoint()
            else -> context.handleChallenge()
        }
    }

    /**
     * Handles the SAML callback (ACS endpoint).
     * Processes the SAML response and validates the assertion.
     */
    private suspend fun AuthenticationContext.handleSamlCallback() {
        try {
            val parameters = call.receiveParameters()
            val samlResponseBase64 = parameters["SAMLResponse"]
            val relayState = parameters["RelayState"]

            if (samlResponseBase64 == null) {
                challenge(SAML_AUTH_KEY, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                    challengeFunction(SamlChallengeContext(call), AuthenticationFailedCause.NoCredentials)
                    challenge.complete()
                }
                return
            }

            val session = call.sessions.get<SamlSession>()
            val expectedRequestId = session?.requestId

            val credentials = responseProcessor.processResponse(samlResponseBase64, expectedRequestId)
            val principal = authenticationFunction(call, credentials)

            if (principal == null) {
                challenge(SAML_AUTH_KEY, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    challengeFunction(SamlChallengeContext(call), AuthenticationFailedCause.InvalidCredentials)
                    challenge.complete()
                }
                return
            }

            this.principal(name, principal)
            call.sessions.clear<SamlSession>()

            when {
                relayState.isNullOrBlank() -> return
                relayValidator.validate(url = relayState) -> call.respondRedirect(url = relayState)
                else -> logger.warn("RelayState URL not in allowlist, ignoring: $relayState")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SamlValidationException) {
            error(SAML_AUTH_KEY, AuthenticationFailedCause.Error(e.message ?: "SAML validation failed"))
        } catch (e: Exception) {
            val message = "SAML processing error:  ${e.message ?: "Unknown error"}"
            error(SAML_AUTH_KEY, AuthenticationFailedCause.Error(message))
        }
    }

    /**
     * Handles the challenge phase (initiates SAML authentication).
     * Generates an AuthnRequest and sends it to the IdP using the configured binding.
     */
    private fun AuthenticationContext.handleChallenge() {
        challenge(SAML_AUTH_KEY, cause = AuthenticationFailedCause.NoCredentials) { challenge, call ->
            try {
                when (config.authnRequestBinding) {
                    SamlBinding.HttpRedirect -> {
                        val result = buildAuthnRequestRedirect(
                            acsUrl = acsUrl,
                            spEntityId = spEntityId,
                            idpSsoUrl = idpMetadata.getSsoUrlFor(SamlBinding.HttpRedirect),
                            relayState = call.request.uri,
                            signingCredential = signingCredential,
                            nameIdFormat = config.nameIdFormat,
                            forceAuthn = config.forceAuthn,
                            signatureAlgorithm = config.signatureAlgorithm,
                            requestedAuthnContext = requestedAuthnContext
                        )
                        call.sessions.set(SamlSession(requestId = result.messageId))
                        call.respondRedirect(result.redirectUrl)
                    }

                    SamlBinding.HttpPost -> {
                        val postData = buildAuthnRequestPost(
                            acsUrl = acsUrl,
                            spEntityId = spEntityId,
                            relayState = call.request.uri,
                            forceAuthn = config.forceAuthn,
                            nameIdFormat = config.nameIdFormat,
                            signingCredential = signingCredential,
                            digestAlgorithm = config.digestAlgorithm,
                            requestedAuthnContext = requestedAuthnContext,
                            signatureAlgorithm = config.signatureAlgorithm,
                            idpSsoUrl = idpMetadata.getSsoUrlFor(SamlBinding.HttpPost),
                        )
                        call.sessions.set(SamlSession(postData.requestId))
                        call.respondText(postData.toAutoSubmitHtml(), ContentType.Text.Html)
                    }
                }
                challenge.complete()
            } catch (e: Exception) {
                logger.error("Failed to initiate SAML authentication", e)
                challengeFunction(
                    SamlChallengeContext(call),
                    AuthenticationFailedCause.Error("Failed to initiate SAML")
                )
                if (!challenge.completed && call.response.status() != null) {
                    challenge.complete()
                }
            }
        }
    }

    /**
     * Handles the Single Logout (SLO) endpoint.
     * Routes to either LogoutRequest or LogoutResponse handling based on the parameters.
     */
    private suspend fun AuthenticationContext.handleSloEndpoint() {
        try {
            val parameters = when (call.request.httpMethod) {
                HttpMethod.Get -> call.request.queryParameters
                HttpMethod.Post -> call.receiveParameters()
                else -> {
                    logger.debug("SLO endpoint called with unsupported method: {}", call.request.httpMethod)
                    return call.respond(HttpStatusCode.MethodNotAllowed)
                }
            }

            val samlRequest = parameters["SAMLRequest"]
            val samlResponse = parameters["SAMLResponse"]

            when {
                samlRequest != null -> handleIdpLogoutRequest(samlRequest, parameters)
                samlResponse != null -> handleLogoutResponse(samlResponse, parameters)
                else -> {
                    logger.debug("SLO endpoint called without SAMLRequest or SAMLResponse")
                    call.respond(HttpStatusCode.BadRequest, "Missing SAMLRequest or SAMLResponse parameter")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("SLO endpoint error", e)
            call.respond(HttpStatusCode.InternalServerError, "Logout processing failed")
        }
    }

    /**
     * Handles IdP-initiated LogoutRequest.
     * Processes the request, terminates the local session, and sends back a LogoutResponse.
     */
    private suspend fun AuthenticationContext.handleIdpLogoutRequest(
        samlRequestBase64: String,
        parameters: Parameters
    ) {
        val processor = checkNotNull(logoutProcessor)
        try {
            val logoutRequest = processor.processRequest(
                samlRequestBase64 = samlRequestBase64,
                binding = call.request.samlBinding(),
                queryString = call.request.queryString(),
                signatureParam = parameters["Signature"],
                signatureAlgorithmParam = parameters["SigAlg"],
            )
            call.sessions.clear<SamlSession>()

            val idpSloUrl = requireNotNull(idpMetadata.sloUrl) { "IdP SLO URL not found" }
            val logoutResponse = buildLogoutResponseRedirect(
                spEntityId = spEntityId,
                idpSloUrl = idpSloUrl,
                inResponseTo = logoutRequest.requestId,
                statusCodeValue = StatusCode.SUCCESS,
                relayState = parameters["RelayState"],
                signingCredential = signingCredential,
                signatureAlgorithm = config.signatureAlgorithm
            )

            call.respondRedirect(logoutResponse.redirectUrl)
        } catch (e: SamlValidationException) {
            logger.error("IdP LogoutRequest validation failed", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid logout request")
        }
    }

    /**
     * Handles LogoutResponse from IdP (after SP-initiated logout).
     * Validates the response and completes the logout process.
     */
    private suspend fun AuthenticationContext.handleLogoutResponse(
        samlResponseBase64: String,
        parameters: Parameters
    ) {
        val processor = checkNotNull(logoutProcessor) { "Logout response processor not initialized" }

        try {
            val session = call.sessions.get<SamlSession>()
            val expectedRequestId = session?.logoutRequestId

            val result = processor.processResponse(
                samlResponseBase64 = samlResponseBase64,
                expectedRequestId = expectedRequestId,
                binding = call.request.samlBinding(),
                queryString = call.request.queryString(),
                signatureParam = parameters["Signature"],
                signatureAlgorithmParam = parameters["SigAlg"],
            )

            // Clear the SAML session regardless of status
            call.sessions.clear<SamlSession>()

            if (!result.isSuccess) {
                val statusMessage = result.statusMessage ?: "No message"
                logger.warn("IdP logout failed with status ${result.statusCode}: $statusMessage")
                call.respond(HttpStatusCode.BadGateway, "IdP logout failed: $statusMessage")
                return
            }

            // Redirect to RelayState or respond with success
            val relayState = parameters["RelayState"]
            if (!relayState.isNullOrBlank() && relayValidator.validate(url = relayState)) {
                call.respondRedirect(relayState)
            } else {
                call.respond(HttpStatusCode.OK, "Logout completed")
            }
        } catch (e: SamlValidationException) {
            logger.debug("LogoutResponse validation failed", e)
            call.respond(HttpStatusCode.BadRequest, "Invalid logout response")
        }
    }
}

internal class RelayValidator(private val allowedRelayStateUrls: List<String>?) {
    /**
     * Validates that the relay state URL is allowed for redirect.
     */
    fun validate(url: String): Boolean = when {
        allowedRelayStateUrls == null -> true
        // Reject dangerous patterns
        url.startsWith("//") || url.contains("\\") || url.any { it.isISOControl() } -> false
        // Allow relative paths
        url.startsWith("/") -> {
            if (allowedRelayStateUrls.isEmpty()) return true
            val pathPrefixes = allowedRelayStateUrls.filter { it.startsWith("/") && !it.startsWith("//") }
            pathPrefixes.any { prefix ->
                url.startsWith(prefix) && (url == prefix || prefix.endsWith("/"))
            }
        }
        // Validate absolute URLs
        else -> {
            allowedRelayStateUrls.any { prefix -> isAllowedAbsoluteRelayState(url, prefix) }
        }
    }

    /**
     * Validates an absolute URL against an allowed prefix.
     * Checks a scheme, host, port, and path with segment boundary validation.
     */
    private fun isAllowedAbsoluteRelayState(targetUrl: String, allowedPrefix: String): Boolean {
        val target = runCatching { Url(targetUrl) }.getOrNull() ?: return false
        val allowed = runCatching { Url(allowedPrefix) }.getOrNull() ?: return false

        // Reject URLs with userinfo (user:pass@host) - bypass technique
        if (target.user != null || target.password != null) return false

        // Only allow http and https schemes
        if (target.protocol.name !in listOf("http", "https")) return false
        if (allowed.protocol.name !in listOf("http", "https")) return false

        // Exact protocol match (case-insensitive)
        if (!target.protocol.name.equals(allowed.protocol.name, ignoreCase = true)) return false

        // Exact host match (case-insensitive per RFC 3986)
        if (!target.host.equals(allowed.host, ignoreCase = true)) return false

        // Exact port match
        if (target.port != allowed.port) return false

        // Path prefix match with segment boundary check
        val allowedPath = allowed.encodedPath.ifBlank { "/" }
        val targetPath = target.encodedPath.ifBlank { "/" }

        if (!targetPath.startsWith(allowedPath)) return false
        if (targetPath == allowedPath) return true
        if (allowedPath.endsWith("/")) return true
        return targetPath.getOrNull(allowedPath.length) == '/'
    }
}

/**
 * Session data for SAML authentication.
 * Stores the AuthnRequest ID for InResponseTo validation and optional LogoutRequest ID for SLO.
 *
 * When using SAML authentication, you need to install the Sessions plugin
 * and register this session type:
 *
 * ```kotlin
 * install(Sessions) {
 *     cookie<SamlSession>("SAML_SESSION")
 * }
 * ```
 *
 * @property requestId The ID of the AuthnRequest, used for InResponseTo validation
 * @property logoutRequestId The ID of the LogoutRequest (for Single Logout), used for InResponseTo validation
 */
@Serializable
public class SamlSession(
    public val requestId: String,
    public val logoutRequestId: String? = null
)

private val SAML_AUTH_KEY: Any = "SAMLAuth"

private fun isAbsoluteUrl(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://")
}

private fun ApplicationRequest.samlBinding(): SamlBinding = when (httpMethod) {
    HttpMethod.Post -> SamlBinding.HttpPost
    HttpMethod.Get -> SamlBinding.HttpRedirect
    else -> error("Unsupported HTTP method: $httpMethod")
}
