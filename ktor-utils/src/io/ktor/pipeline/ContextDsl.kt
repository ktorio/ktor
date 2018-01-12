package io.ktor.pipeline

/**
 * DslMarker for pipeline execution context
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE)
annotation class ContextDsl