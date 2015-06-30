package org.jetbrains.ktor.tests

import org.jetbrains.spek.api.*

fun on(comment: String, body: On.() -> Unit) {
    object : On {
        override fun it(description: String, body: It.() -> Unit) {
            object : It {}.body()
/*
            try {
                object : It {}.body()
            } catch(e: Throwable) {
                throw SpecificationFailedException(description, e)
            }
*/
        }
    }.body()
}

class SpecificationFailedException(message: String, e: Throwable) : Exception(message, e)
