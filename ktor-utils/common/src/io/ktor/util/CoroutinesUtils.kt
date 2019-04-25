package io.ktor.util

import kotlinx.coroutines.*

/**
 * Print [Job] children tree.
 */
@InternalAPI
fun Job.printDebugTree(offset: Int = 0) {
    println(" ".repeat(offset) + this)

    children.forEach {
        it.printDebugTree(offset + 2)
    }

    if (offset == 0) println()
}
