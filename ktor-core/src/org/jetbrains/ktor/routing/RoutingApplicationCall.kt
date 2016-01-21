package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*

/**
 * Represents an application call being handled by [Routing]
 */
open class RoutingApplicationCall(call: ApplicationCall,
                                  val route: RoutingEntry,
                                  val parameters: ValuesMap) : ApplicationCall by call