package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.util.*
import kotlin.reflect.*

interface Credential
interface Principal

interface AuthBuilder<C: ApplicationCall> {
    fun intercept(interceptor: C.(C.() -> ApplicationCallResult) -> ApplicationCallResult)

    fun success(interceptor: C.(AuthContext, C.(AuthContext) -> ApplicationCallResult) -> ApplicationCallResult)
    fun fail(interceptor: C.(C.() -> ApplicationCallResult) -> ApplicationCallResult)
}

abstract class AuthBuilderBase<C: ApplicationCall> : AuthBuilder<C> {
    val successHandlers = InterceptableChain2<C, AuthContext, ApplicationCallResult>()
    val failureHandlers = InterceptableChain1<C, ApplicationCallResult>()

    override fun success(interceptor: C.(AuthContext, C.(AuthContext) -> ApplicationCallResult) -> ApplicationCallResult) {
        successHandlers.intercept(interceptor)
    }

    override fun fail(interceptor: C.(C.() -> ApplicationCallResult) -> ApplicationCallResult) {
        failureHandlers.intercept(interceptor)
    }

    fun fireSuccess(ctx: C, auth: AuthContext, next: C.() -> ApplicationCallResult): ApplicationCallResult {
        return successHandlers.call(ctx, auth) { ctx, auth ->
            ctx.next()
        }
    }

    fun fireFailure(ctx: C, next: C.() -> ApplicationCallResult): ApplicationCallResult = failureHandlers.call(ctx, next)
}

private class RoutingEntryAuthBuilder(val entry: RoutingEntry) : AuthBuilderBase<RoutingApplicationCall>() {

    override fun intercept(interceptor: RoutingApplicationCall.(RoutingApplicationCall.() -> ApplicationCallResult) -> ApplicationCallResult) {
        entry.intercept(interceptor)
    }
}

fun RoutingEntry.auth(block: AuthBuilder<RoutingApplicationCall>.() -> Unit) {
    val builder = RoutingEntryAuthBuilder(this)
    builder.block()
    builder.finishAuth()
}

private fun <C: ApplicationCall> AuthBuilderBase<C>.finishAuth() {
    intercept { next ->
        if (AuthContext.AttributeKey in attributes) {
            attributes[AuthContext.AttributeKey].let { auth ->
                if (auth.hasPrincipals()) {
                    fireSuccess(this, auth, next)
                } else {
                    fireFailure(this, next)
                }
            }
        } else {
            fireFailure(this, next)
        }
    }
}

class AuthContext internal constructor() {
    private val collectedCredentials = ArrayList<Credential>()
    private val collectedPrincipals = ArrayList<Principal>()
    private val collectedFailures = HashMap<Credential, MutableList<Throwable>>()

    val foundCredentials: List<Credential>
        get() = synchronized(this) { collectedCredentials.toList() }

    val foundPrincipals: List<Principal>
        get() = synchronized(this) { collectedPrincipals.toList() }

    val failures: Map<Credential, List<Throwable>>
        get() = synchronized(this) { collectedFailures.mapValues { it.value.toList() } }

    inline fun <reified K : Credential> credentials(): List<K> = credentials(K::class)

    @Suppress("UNCHECKED_CAST")
    fun <K : Credential> credentials(type: KClass<K>): List<K> = synchronized(this) {
        collectedCredentials.filter { type.java.isInstance(it) } as List<K>
    }

    inline fun <reified P : Principal> principals(): List<P> = principals(P::class)
    inline fun <reified P : Principal> principal(): P? = principals<P>().singleOrNull()

    @Suppress("UNCHECKED_CAST")
    fun <P : Principal> principals(type: KClass<P>): List<P> = synchronized(this) {
        collectedPrincipals.filter { type.java.isInstance(it) } as List<P>
    }

    fun addCredential(credential: Credential) {
        synchronized(this) {
            collectedCredentials.add(credential)
        }
    }

    fun addPrincipals(principals: List<Principal>) {
        if (principals.isNotEmpty()) {
            synchronized(this) {
                collectedPrincipals.addAll(principals)
            }
        }
    }

    fun removePrincipals(principals: List<Principal>) {
        if (principals.isNotEmpty()) {
            synchronized(this) {
                collectedPrincipals.removeAll(principals)
            }
        }
    }

    fun addPrincipal(principal: Principal) {
        synchronized(this) {
            collectedPrincipals.add(principal)
        }
    }

    fun addFailure(credential: Credential, t: Throwable) {
        synchronized(this) {
            collectedFailures.getOrPut(credential) { ArrayList() }.add(t)
        }
    }

    fun hasPrincipals(): Boolean = synchronized(this) { collectedPrincipals.isNotEmpty() }

    companion object {
        val AttributeKey = org.jetbrains.ktor.util.AttributeKey<AuthContext>()
        internal fun from(call: ApplicationCall) = call.attributes.computeIfAbsent(AttributeKey) { AuthContext() }
    }
}

val ApplicationCall.authContext: AuthContext
    get() = AuthContext.from(this)

val ApplicationCall.principals: List<Principal>
    get() = authContext.foundPrincipals

inline fun <reified P: Principal> ApplicationCall.principals() = authContext.principals<P>()

fun <K: Credential, C: ApplicationCall> AuthBuilder<C>.extractCredentials(block: C.() -> K?) {
    intercept { next ->
        val p = block()

        if (p != null) {
            authContext.addCredential(p)
        }

        next()
    }
}

inline fun <reified K : Credential, C: ApplicationCall> AuthBuilder<C>.verifyBatchTypedWith(noinline block: C.(List<K>) -> List<Principal>) {
    verifyBatchTypedWith(K::class, block)
}

fun <K : Credential, C: ApplicationCall> AuthBuilder<C>.verifyBatchTypedWith(klass: KClass<K>, block: C.(List<K>) -> List<Principal>) {
    intercept { next ->
        val auth = authContext

        auth.credentials(klass).let { found ->
            if (found.isNotEmpty()) {
                auth.addPrincipals(block(found))
            }

            next()
        }
    }
}

inline fun <reified K : Credential, C: ApplicationCall> AuthBuilder<C>.verifyWith(noinline block: C.(K) -> Principal?) {
    verifyWith(K::class, block)
}

fun <K : Credential, C: ApplicationCall> AuthBuilder<C>.verifyWith(klass: KClass<K>, block: C.(K) -> Principal?) {
    intercept { next ->
        val auth = authContext

        auth.credentials(klass).let { found ->
            auth.addPrincipals(found.map {
                try {
                    block(it)
                } catch (t: Throwable) {
                    auth.addFailure(it, t)
                    null
                }
            }.filterNotNull())

            next()
        }
    }
}

fun <C: ApplicationCall> AuthBuilder<C>.verifyBatchAll(block: C.(List<Credential>) -> List<Principal>) {
    verifyBatchTypedWith(block)
}

inline fun <reified P : Principal, C: ApplicationCall> AuthBuilder<C>.postVerify(crossinline predicate: C.(P) -> Boolean) {
    intercept { next ->
        val auth = authContext

        auth.principals(P::class).let { found ->
            val discarded = found.filterNot { predicate(it) }
            auth.removePrincipals(discarded)

            next()
        }
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
            .toMapBy({ it.groups[1]!!.value }, { it.groups[2]!!.value.unescapeIfQuoted() })

    return HttpAuthHeader.Parameterized(authScheme, parameters)
}

public fun ApplicationResponse.sendAuthenticationRequest(vararg challenges: HttpAuthHeader = arrayOf(HttpAuthHeader.basicAuthChallenge("ktor"))): ApplicationCallResult {
    require(challenges.isNotEmpty()) { "it should be at least one challenge requested, for example Basic" }

    status(HttpStatusCode.Unauthorized)
    contentType(ContentType.Text.Plain)
    headers.append(HttpHeaders.WWWAuthenticate, challenges.joinToString(", ") { it.render() })
    streamText("Not authorized")
    return ApplicationCallResult.Handled
}
