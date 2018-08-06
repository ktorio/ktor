package io.ktor.util

actual fun random(bound: Int): Int = platform.posix.random().toInt() % bound
