package io.ktor.util.pipeline

/**
 * DslMarker for pipeline execution context
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class ContextDsl