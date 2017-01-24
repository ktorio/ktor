package org.jetbrains.ktor.pipeline

interface PipelineContext<out TSubject : Any> {
    val subject: TSubject

    suspend fun proceed()
}