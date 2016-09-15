package org.jetbrains.ktor.pipeline

sealed class PipelineControl : Throwable() {
    object Completed : PipelineControl()
    object Paused : PipelineControl()
    object Continue : PipelineControl()

    @Suppress("unused", "VIRTUAL_MEMBER_HIDDEN") // implicit override
    fun fillInStackTrace(): Throwable? {
        return null
    }
}