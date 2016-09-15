package org.jetbrains.ktor.pipeline

internal class PipelineBlock(val function: PipelineContext<Any>.(Any) -> Unit) {
    val successes = mutableListOf<PipelineContext<Any>.(Any) -> Unit>()
    val failures = mutableListOf<PipelineContext<Any>.(Any) -> Unit>()
}
