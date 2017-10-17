package io.ktor.pipeline

import io.ktor.application.ApplicationCall

val PipelineContext<*, ApplicationCall>.call: ApplicationCall get() = context
val PipelineContext<*, ApplicationCall>.application get() = call.application
