@file:Suppress("KDocMissingDocumentation", "DEPRECATION")

package io.ktor.pipeline

@Deprecated("Import it from another package", level = DeprecationLevel.ERROR)
typealias ContextDsl = io.ktor.util.pipeline.ContextDsl

@Deprecated("Import from another package", level = DeprecationLevel.ERROR)
typealias InvalidPhaseException = io.ktor.util.pipeline.InvalidPhaseException

@Deprecated("Import it from another package", level = DeprecationLevel.ERROR)
typealias Pipeline<TSubject, TContext> = io.ktor.util.pipeline.Pipeline<TSubject, TContext>

@Deprecated("Import it from another package", level = DeprecationLevel.ERROR)
typealias PipelineContext<TSubject, TContext> = io.ktor.util.pipeline.PipelineContext<TSubject, TContext>

@Deprecated("Import it from another package", level = DeprecationLevel.ERROR)
typealias PipelineInterceptor<TSubject, TContext> = io.ktor.util.pipeline.PipelineInterceptor<TSubject, TContext>

@Deprecated("Import it from another package", level = DeprecationLevel.ERROR)
typealias PipelinePhase = io.ktor.util.pipeline.PipelinePhase

@Suppress("DEPRECATION_ERROR")
@Deprecated("Import it from another package", level = DeprecationLevel.ERROR)
suspend inline fun <TContext : Any> Pipeline<Unit, TContext>.execute(context: TContext) = execute(context, Unit)

@Suppress("DEPRECATION_ERROR")
@Deprecated("Import it from another package", level = DeprecationLevel.ERROR)
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
