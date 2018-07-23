package io.ktor.util

import java.util.*

private val GENERATOR = Random()

/**
 * Generate random [Int]
 */
actual fun random(bound: Int): Int = GENERATOR.nextInt(bound)
