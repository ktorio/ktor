/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.uri
import io.ktor.util.logging.trace
import io.ktor.utils.io.InternalAPI

/**
 * A single step of an [AuthenticationPlan].
 *
 * @property owner index of the outermost registration that introduced [provider].
 */
internal class AuthenticationStep(val provider: AuthenticationProvider, val owner: Int)

/*
 * What a [PhaseStrategy] decides about the rest of its phase.
 */
internal enum class StepResult { Next, Complete, Reject }

/*
 * Outcome of running a [Phase] for a single `authenticate` block.
 */
internal enum class PhaseResult { Completed, Pending, Rejected }

/**
 * How a [Phase] reacts to authentication attempts. Strategies are stateless and shared by all calls.
 */
internal sealed interface PhaseStrategy {
    /** Checked before each attempt, since a principal may come from outside this phase. */
    context(ctx: AuthenticationContext)
    fun beforeAttempt(): StepResult

    fun afterAttempt(succeeded: Boolean): StepResult

    /** Every provider must authenticate the call. */
    object AllRequired : PhaseStrategy {
        context(ctx: AuthenticationContext)
        override fun beforeAttempt(): StepResult = StepResult.Next

        override fun afterAttempt(succeeded: Boolean): StepResult =
            if (succeeded) StepResult.Next else StepResult.Reject
    }

    /** Providers are tried until the call has a principal. */
    object FirstSuccessful : PhaseStrategy {
        context(ctx: AuthenticationContext)
        override fun beforeAttempt(): StepResult =
            if (ctx.hasPrincipal) StepResult.Complete else StepResult.Next

        override fun afterAttempt(succeeded: Boolean): StepResult =
            if (succeeded) StepResult.Complete else StepResult.Next
    }
}

internal class Phase(val steps: List<AuthenticationStep>, val strategy: PhaseStrategy)

/**
 * The providers of nested `authenticate` blocks grouped into [phases] that run in order.
 *
 * @property allowsAnonymous whether the call may proceed without a principal when no invalid credentials were sent.
 */
@OptIn(InternalAPI::class)
internal class AuthenticationPlan(
    val registrations: List<AuthenticateProvidersRegistration>,
    val phases: List<Phase>,
) {
    val allowsAnonymous = registrations.all { it.strategy == AuthenticationStrategy.Optional }
    val lastIndex: Int get() = registrations.lastIndex
}

/**
 * Progress of an [AuthenticationPlan] for a single call, shared by all `authenticate` blocks on the route's path.
 */
internal class AuthenticationRun(val plan: AuthenticationPlan, val context: AuthenticationContext) {
    private var phaseIndex = 0
    private var stepIndex = 0

    // An earlier mechanism (e.g. typed authentication) may have already authenticated the call.
    private var finished = context.hasPrincipal

    /**
     * Runs the steps owned by registrations up to [ownIndex], stopping at the first step owned by a nested block.
     */
    suspend fun execute(call: ApplicationCall, ownIndex: Int) {
        if (finished) {
            return
        }

        while (phaseIndex < plan.phases.size) {
            val phase = plan.phases[phaseIndex]
            val result = context(context) { phase.run(call, ownIndex) }
            when (result) {
                PhaseResult.Pending -> return

                PhaseResult.Rejected -> return finish { context.executeChallenges(call) }

                PhaseResult.Completed -> {
                    phaseIndex++
                    stepIndex = 0
                }
            }
        }

        finish {
            if (context.hasPrincipal) return
            if (plan.allowsAnonymous && context.hasNoInvalidCredentials) {
                LOGGER.trace { "Authentication is optional and no credentials were provided for ${call.request.uri}" }
                return
            }
            context.executeChallenges(call)
        }
    }

    context(ctx: AuthenticationContext)
    private suspend fun Phase.run(call: ApplicationCall, ownIndex: Int): PhaseResult {
        while (stepIndex < steps.size) {
            when (strategy.beforeAttempt()) {
                StepResult.Next -> {}
                StepResult.Complete -> return PhaseResult.Completed
                StepResult.Reject -> return PhaseResult.Rejected
            }
            val step = steps[stepIndex]
            if (step.owner > ownIndex) {
                return PhaseResult.Pending
            }
            stepIndex++

            val provider = step.provider
            if (provider.skipWhen.any { skipCondition -> skipCondition(call) }) {
                LOGGER.trace { "Skipping authentication provider ${provider.name} for ${call.request.uri}" }
                continue
            }
            val succeeded = provider.tryAuthenticate(call)
            return when (strategy.afterAttempt(succeeded)) {
                StepResult.Next -> continue
                StepResult.Complete -> PhaseResult.Completed
                StepResult.Reject -> PhaseResult.Rejected
            }
        }
        return PhaseResult.Completed
    }

    private inline fun finish(block: () -> Unit) {
        finished = true
        block()
    }
}

/**
 * Runs this provider for [call] and returns whether it added a principal.
 */
context(context: AuthenticationContext)
private suspend fun AuthenticationProvider.tryAuthenticate(call: ApplicationCall): Boolean {
    LOGGER.trace {
        "Trying to authenticate ${call.request.uri} with $name"
    }
    val principalsBefore = context._principal.principals.size
    onAuthenticate(context)
    val succeeded = context._principal.principals.size > principalsBefore
    LOGGER.trace {
        "Authentication ${if (succeeded) "succeeded" else "failed"} for ${call.request.uri} with $name"
    }
    return succeeded
}

private val AuthenticationContext.hasPrincipal: Boolean
    get() = _principal.principals.isNotEmpty()

private val AuthenticationContext.hasNoInvalidCredentials: Boolean
    get() = allFailures.none { it == AuthenticationFailedCause.InvalidCredentials }
