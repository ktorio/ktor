package io.ktor.jrpc.model

import io.ktor.application.ApplicationCall
import io.ktor.pipeline.PipelineContext

/**
 * Class defining route item for JrpcRouter
 * Contains object class to which input params must be converted
 * @param T is the input type of params
 */
class JrpcRouteItem<T>(
        /**
         * The block of method code
         */
        val handler: PipelineContext<Unit, ApplicationCall>.(T) -> Any?,
        /**
         * The class of method input params
         */
        val clazz: Class<T>)