package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.*
import kotlin.properties.*

class AuthenticationContext(val call: ApplicationCall) {
    var principal by Delegates.vetoable<Principal?>(null) { _, old, _ -> require(old == null); true }
    val errors = HashMap<Any, NotAuthenticatedCause>()

    private val challengesCollector = mutableListOf<Pair<NotAuthenticatedCause, suspend PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>>()

    val challenges: List<suspend PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>
        get() = challengesCollector.filter { it.first !is NotAuthenticatedCause.Error }.sortedBy {
            when (it.first) {
                NotAuthenticatedCause.InvalidCredentials -> 1
                NotAuthenticatedCause.NoCredentials -> 2
                else -> throw NoWhenBranchMatchedException("${it.first}")
            }
        }.map { it.second }

    val allChallenges: List<Pair<NotAuthenticatedCause, suspend PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit>>
        get() = challengesCollector.toList()

    fun principal(principal: Principal) {
        this.principal = principal
    }

    fun hasPrincipal() = principal != null

    inline fun <reified T : Principal> principal(): T? = principal as? T

    fun error(key: Any, cause: NotAuthenticatedCause) {
        errors[key] = cause
    }

    suspend fun challenge(key: Any, cause: NotAuthenticatedCause, function: suspend PipelineContext<AuthenticationProcedureChallenge>.(AuthenticationProcedureChallenge) -> Unit) {
        error(key, cause)
        challengesCollector.add(cause to function)
    }

    override fun toString(): String {
        return "AuthenticationProcedureContext(call=$call)"
    }


    companion object {
        val AttributeKey = AttributeKey<AuthenticationContext>("AuthContext")

        internal fun from(call: ApplicationCall) = call.attributes.computeIfAbsent(AttributeKey) { AuthenticationContext(call) }
    }
}