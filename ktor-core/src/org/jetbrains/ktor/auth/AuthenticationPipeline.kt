package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.*
import kotlin.properties.*

class AuthenticationProcedure() {
    val pipeline = Pipeline<AuthenticationProcedureContext>()

    fun authenticate(authenticator: PipelineContext<AuthenticationProcedureContext>.(AuthenticationProcedureContext) -> Unit) {
        pipeline.intercept(authenticator)
    }
}

fun InterceptApplicationCall.authentication(procedure: AuthenticationProcedure.() -> Unit) {
    val authenticationProcedure = AuthenticationProcedure().apply(procedure).apply {
        pipeline.intercept { procedure ->
            val principal = procedure.principal
            if (principal == null) {
                val challenges = procedure.challenges
                if (challenges.isNotEmpty()) {
                    val challengePipeline = Pipeline(challenges)
                    challengePipeline.intercept {
                        if (subject.success) {
                            finishAll()
                        }
                    }
                    procedure.call.fork(AuthenticationProcedureChallenge(), challengePipeline)
                }
            }
        }
    }
    intercept {
        val context = AuthenticationProcedureContext(call)
        call.attributes.put(AuthenticationProcedureContext.AttributeKey, context)
        call.fork(context, authenticationProcedure.pipeline)
    }
}

class AuthenticationProcedureChallenge {
    @Volatile
    var success = false
        private set

    fun success() {
        success = true
    }

    override fun toString(): String = "AuthenticationProcedureChallenge"
}

sealed class NotAuthenticatedCause {
    object NoCredentials : NotAuthenticatedCause()
    object InvalidCredentials : NotAuthenticatedCause()
    open class Error(val cause: String) : NotAuthenticatedCause()
}

class AuthenticationProcedureContext(val call: ApplicationCall) {
    var principal by Delegates.vetoable<Principal?>(null) { p, old, new -> require(old == null); true }
    val errors = HashMap<Any, NotAuthenticatedCause>()

    private val challengesCollector = mutableListOf<Pair<NotAuthenticatedCause, PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>>()

    val challenges: List<PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>
        get() = challengesCollector.filter { it.first !is NotAuthenticatedCause.Error }.sortedBy { when (it.first) {
            NotAuthenticatedCause.InvalidCredentials -> 1
            NotAuthenticatedCause.NoCredentials -> 2
            else -> throw NoWhenBranchMatchedException("${it.first}")
        } }.map { it.second }

    val allChallenges: List<Pair<NotAuthenticatedCause, PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>>
        get() = challengesCollector.toList()

    fun principal(principal: Principal) {
        this.principal = principal
    }

    fun hasPrincipal() = principal != null

    inline fun <reified T : Principal> principal(): T? = principal as? T

    fun error(key: Any, cause: NotAuthenticatedCause) {
        errors[key] = cause
    }

    fun challenge(key: Any, cause: NotAuthenticatedCause, function: PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit) {
        error(key, cause)
        challengesCollector.add(cause to function)
    }

    override fun toString(): String {
        return "AuthenticationProcedureContext(call=$call)"
    }


    companion object {
        val AttributeKey = AttributeKey<AuthenticationProcedureContext>("AuthContext")

        internal fun from(call: ApplicationCall) = call.attributes.computeIfAbsent(AttributeKey) { AuthenticationProcedureContext(call) }
    }
}


val ApplicationCall.authentication: AuthenticationProcedureContext
    get() = AuthenticationProcedureContext.from(this)


inline fun <reified P : Principal> ApplicationCall.principal() = authentication.principal<P>()
