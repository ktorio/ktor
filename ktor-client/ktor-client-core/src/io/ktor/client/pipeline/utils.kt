package io.ktor.client.pipeline

import io.ktor.pipeline.*

inline fun <reified TSubject : Any, TContext : Any> Pipeline<*, TContext>.intercept(
        phase: PipelinePhase,
        crossinline block: PipelineContext<TSubject, TContext>.(TSubject) -> Unit) {

    intercept(phase) interceptor@ { subject ->
        subject as? TSubject ?: return@interceptor
        val reinterpret = this as? PipelineContext<TSubject, TContext>
        reinterpret?.block(subject)
    }
}