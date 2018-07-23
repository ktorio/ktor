package io.ktor.util.cio

import java.io.*


open class ChannelIOException(message: String, exception: Throwable) : IOException(message, exception)
class ChannelWriteException(message: String = "Cannot write to a channel", exception: Throwable) : ChannelIOException(message, exception)
class ChannelReadException(message: String = "Cannot read from a channel", exception: Throwable) : ChannelIOException(message, exception)

