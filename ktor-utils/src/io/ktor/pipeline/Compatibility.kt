package io.ktor.pipeline

typealias ContextDsl = io.ktor.util.pipeline.ContextDsl

typealias InvalidPhaseException = io.ktor.util.pipeline.InvalidPhaseException

typealias Pipeline<TSubject, TContext> = io.ktor.util.pipeline.Pipeline<TSubject, TContext>

typealias PipelineContext<TSubject, TContext> = io.ktor.util.pipeline.PipelineContext<TSubject, TContext>

typealias PipelineInterceptor<TSubject, TContext> = io.ktor.util.pipeline.PipelineInterceptor<TSubject, TContext>

typealias PipelinePhase = io.ktor.util.pipeline.PipelinePhase

suspend inline fun <TContext : Any> Pipeline<Unit, TContext>.execute(context: TContext) = execute(context, Unit)

inline fun <reified TSubject : Any, TContext : Any> Pipeline<*, TContext>.intercept(
    phase: PipelinePhase,
    noinline block: suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit
) {

    intercept(phase) interceptor@{ subject ->
        subject as? TSubject ?: return@interceptor
        @Suppress("UNCHECKED_CAST")
        val reinterpret = this as? PipelineContext<TSubject, TContext>
        reinterpret?.block(subject)
    }
}
