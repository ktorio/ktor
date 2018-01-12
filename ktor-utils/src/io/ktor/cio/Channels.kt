package io.ktor.cio

import java.io.*


open class ChannelIOException(message: String, exception: Exception) : IOException(message, exception)
class ChannelWriteException(message: String = "Cannot write to a channel", exception: Exception) : ChannelIOException(message, exception)
class ChannelReadException(message: String = "Cannot read from a channel", exception: Exception) : ChannelIOException(message, exception)

