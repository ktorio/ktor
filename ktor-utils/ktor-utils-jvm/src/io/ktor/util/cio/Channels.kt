package io.ktor.util.cio

import java.io.*

/**
 * An exception thrown when an IO error occurred during reading or writing to/from the underlying channel.
 * The typical error is "connection reset" and so on.
 */
open class ChannelIOException(message: String, exception: Throwable) : IOException(message, exception)

/**
 * An exception that is thrown when an IO error occurred during writing to the destination channel.
 * Usually it happens when a remote client closed the connection.
 */
class ChannelWriteException(message: String = "Cannot write to a channel", exception: Throwable) :
    ChannelIOException(message, exception)

/**
 * An exception that is thrown when an IO error occurred during reading from the request channel.
 * Usually it happens when a remote client closed the connection.
 */
class ChannelReadException(message: String = "Cannot read from a channel", exception: Throwable) :
    ChannelIOException(message, exception)

