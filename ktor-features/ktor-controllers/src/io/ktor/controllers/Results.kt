package io.ktor.controllers

import io.ktor.application.ApplicationCall
import io.ktor.response.respond
import io.ktor.response.respondText
import kotlin.reflect.KFunction
import kotlin.reflect.full.createType

interface ResultDetector {
    /**
     * Detects how a given function's return value needs to be handled, looking at it's annotations or return type.
     * It is called once during the route mapping, not for each request call.
     * Returns null if it can't handle the given function's return type.
     */
    fun <T> detect(func: KFunction<T>): ResultMapping<T>?
}

/*
 * Maps the result of a method to an action to respond to the request.
 * It is called for every request.
 */
typealias ResultMapping<T> = suspend (ApplicationCall, T?) -> Unit


/****************    Detector implementations    **************/

object ResultDetectors {

    /** For methods that don't return any value (Unit) -> don't respond to the call */
    object UnitDetector : ResultDetector {
        override fun <T> detect(func: KFunction<T>): ResultMapping<T>? {
            if (func.returnType != Unit::class.createType())
                return null
            return { _, _ -> }
        }
    }

    /** For methods returning String -> respond with the resulting String */
    object StringDetector : ResultDetector {
        override fun <T> detect(func: KFunction<T>): ResultMapping<T>? {
            if (func.returnType != String::class.createType())
                return null
            return { call, result -> call.respondText(result as String) }
        }
    }

    /**
     * For any method -> respond with the returned object. See 'content-negotiation' to know how
     * these objects are handled. In case the result is null, there will be nor response.
     * This detector should be the last one in the chain, since it always returns a ResultMapping for the function.
     */
    object ObjectDetector : ResultDetector {
        override fun <T> detect(func: KFunction<T>): ResultMapping<T>? = { call, result ->
            if (result != null) {
                call.respond(result)
            }
        }
    }
}
