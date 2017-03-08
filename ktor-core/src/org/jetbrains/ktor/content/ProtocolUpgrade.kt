package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.pipeline.*
import java.io.*

abstract class ProtocolUpgrade() : HostResponse {
    abstract suspend fun upgrade(call: ApplicationCall, context: PipelineContext<*>, input: ReadChannel, output: WriteChannel, channel: Closeable): Closeable
}