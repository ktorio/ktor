package io.ktor.server.application.plugins.api

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*

/**
 * [Interception] describes how (with what new functionality) some particular phase should be intercepted.
 * It is a wrapper over pipeline.intercept(phase) { ... } and is needed to hide old Plugins API functionality
 * */
internal class Interception<T : Any>(
    val phase: PipelinePhase,
    val action: (Pipeline<T, ApplicationCall>) -> Unit
)

/**
 * Interception class for Call phase
 * */
internal typealias CallInterception = Interception<Unit>

/**
 * Interception class for Receive phase
 * */
internal typealias ReceiveInterception = Interception<ApplicationReceiveRequest>

/**
 * Interception class for Send phase
 * */
internal typealias ResponseInterception = Interception<Any>
