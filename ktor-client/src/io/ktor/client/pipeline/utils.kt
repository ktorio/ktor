package io.ktor.client.pipeline

import io.ktor.pipeline.*
import io.ktor.util.*


inline fun <reified NewSubject : Any, Context : Any> Pipeline<*, Context>.intercept(
        phase: PipelinePhase,
        crossinline block: PipelineContext<NewSubject, Context>.(NewSubject) -> Unit) {
    intercept(phase) interceptor@ { subject ->
        subject as? NewSubject ?: return@interceptor
        safeAs<PipelineContext<NewSubject, Context>>()?.block(subject)
    }
}