package org.jetbrains.ktor.pipeline

enum class PipelineState {
    Executing,

    Finished,

    FinishedAll,

    Failed
}