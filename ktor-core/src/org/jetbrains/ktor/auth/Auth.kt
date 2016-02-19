package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

interface Credential
interface Principal

fun <C : ApplicationCall> InterceptApplicationCall<C>.authenticate(body: PipelineContext<C>.() -> Unit) {
    intercept {
        body()
        val context = call.attributes.getOrNull(AuthenticationContext.AttributeKey)
        if (context != null && context.hasPrincipals()) {
            // ok, just roll on
            return@intercept
        }
        throw Exception("Authentication failed")
    }
}

fun <K : Credential, C : ApplicationCall> PipelineContext<C>.extractCredentials(block: C.() -> K?) {
    val p = call.block()
    if (p != null) {
        call.authentication.addCredential(p)
    }
}

inline fun <reified K : Credential, C : ApplicationCall> PipelineContext<C>.verifyBatchTypedWith(noinline authenticator: C.(List<K>) -> List<Principal>) {
    verifyBatchTypedWith(K::class, authenticator)
}

fun <K : Credential, C : ApplicationCall> PipelineContext<C>.verifyBatchTypedWith(klass: KClass<K>, authenticator: C.(List<K>) -> List<Principal>) {
    val context = call.authentication

    val credentials = context.credentials(klass)
    if (credentials.isNotEmpty()) {
        val principals = call.authenticator(credentials)
        context.addPrincipals(principals)
    }
}

inline fun <reified K : Credential, C : ApplicationCall> PipelineContext<C>.verifyWith(noinline authenticator: C.(K) -> Principal?) {
    verifyWith(K::class, authenticator)
}

fun <K : Credential, C : ApplicationCall> PipelineContext<C>.verifyWith(klass: KClass<K>, authenticator: C.(K) -> Principal?) {
    val auth = call.authentication

    auth.credentials(klass).let { found ->
        auth.addPrincipals(found.map {
            try {
                call.authenticator(it)
            } catch (t: Throwable) {
                auth.addFailure(it, t)
                null
            }
        }.filterNotNull())
    }
}

fun <C : ApplicationCall> PipelineContext<C>.verifyBatchAll(block: C.(List<Credential>) -> List<Principal>) {
    verifyBatchTypedWith(block)
}

inline fun <reified P : Principal, C : ApplicationCall> PipelineContext<C>.postVerify(crossinline predicate: C.(P) -> Boolean) {
    val auth = call.authentication

    auth.principals(P::class).let { found ->
        val discarded = found.filterNot { call.predicate(it) }
        auth.removePrincipals(discarded)
    }
}

public fun ApplicationRequest.parseAuthorizationHeader(): HttpAuthHeader? = authorization()?.let {
    parseAuthorizationHeader(it)
}

private val token68Pattern = "[a-zA-Z0-9\\-\\._~+/]+=*".toRegex()
private val authSchemePattern = "\\S+".toRegex()
private val valuePatternPart = """("((\\.)|[^\\\"])*")|[^\s,]*"""
private val parameterPattern = "\\s*,?\\s*($token68Pattern)\\s*=\\s*($valuePatternPart)\\s*,?\\s*".toRegex()

public fun parseAuthorizationHeader(headerValue: String): HttpAuthHeader? {
    val schemeRegion = authSchemePattern.find(headerValue) ?: return null
    val authScheme = schemeRegion.value
    val remaining = headerValue.substringAfterMatch(schemeRegion).trimStart()

    val token68 = token68Pattern.find(remaining)
    if (token68 != null && remaining.substringAfterMatch(token68).isBlank()) {
        return HttpAuthHeader.Single(authScheme, token68.value)
    }

    val parameters = parameterPattern.findAll(remaining)
            .associateBy({ it.groups[1]!!.value }, { it.groups[2]!!.value.unescapeIfQuoted() })

    return HttpAuthHeader.Parameterized(authScheme, parameters)
}

public fun ApplicationCall.sendAuthenticationRequest(vararg challenges: HttpAuthHeader = arrayOf(HttpAuthHeader.basicAuthChallenge("ktor"))): Unit {
    require(challenges.isNotEmpty()) { "it should be at least one challenge requested, for example Basic" }

    response.status(HttpStatusCode.Unauthorized)
    response.headers.append(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render() })
    respondText(ContentType.Text.Plain, "Not authorized")
}
