package org.jetbrains.ktor.content

import java.io.*

interface StreamContent {
    fun stream(out : OutputStream): Unit
}