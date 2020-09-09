package io.ktor.utils.io.errors

import kotlinx.cinterop.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.internal.utils.*
import platform.posix.*
import kotlin.native.concurrent.*

@Suppress("unused")
@SharedImmutable
private val s: KX_SOCKET = 0.convert() // do not remove! This is required to hold star import for strerror_r

@SharedImmutable
private val KnownPosixErrors = mapOf<Int, String>(
    EBADF to "EBADF",
    EWOULDBLOCK to "EWOULDBLOCK",
    EAGAIN to "EAGAIN",
    EBADMSG to "EBADMSG",
    EINTR to "EINTR",
    EINVAL to "EINVAL",
    EIO to "EIO",
    ECONNREFUSED to "ECONNREFUSED",
    ECONNABORTED to "ECONNABORTED",
    ECONNRESET to "ECONNRESET",
    ENOTCONN to "ENOTCONN",
    ETIMEDOUT to "ETIMEDOUT",
    EOVERFLOW to "EOVERFLOW",
    ENOMEM to "ENOMEM",
    ENOTSOCK to "ENOTSOCK",
    EADDRINUSE to "EADDRINUSE",
    ENOENT to "ENOENT"
)

/**
 * Represents a POSIX error. Could be thrown when a POSIX function returns error code.
 * @property errno error code that caused this exception
 * @property message error text
 */
@ExperimentalIoApi
public sealed class PosixException(public val errno: Int, message: String) : Exception(message) {
    @ExperimentalIoApi
    public class BadFileDescriptorException(message: String) : PosixException(EBADF, message)

    @ExperimentalIoApi
    public class TryAgainException(errno: Int = EAGAIN, message: String) : PosixException(errno, message)

    @ExperimentalIoApi
    public class BadMessageException(message: String) : PosixException(EBADMSG, message)

    @ExperimentalIoApi
    public class InterruptedException(message: String) : PosixException(EINTR, message)

    @ExperimentalIoApi
    public class InvalidArgumentException(message: String) : PosixException(EINVAL, message)

    @ExperimentalIoApi
    public class ConnectionResetException(message: String) : PosixException(ECONNRESET, message)

    @ExperimentalIoApi
    public class ConnectionRefusedException(message: String) : PosixException(ECONNREFUSED, message)

    @ExperimentalIoApi
    public class ConnectionAbortedException(message: String) : PosixException(ECONNABORTED, message)

    @ExperimentalIoApi
    public class NotConnectedException(message: String) : PosixException(ENOTCONN, message)

    @ExperimentalIoApi
    public class TimeoutIOException(message: String) : PosixException(ETIMEDOUT, message)

    @ExperimentalIoApi
    public class NotSocketException(message: String) : PosixException(ENOTSOCK, message)

    @ExperimentalIoApi
    public class AddressAlreadyInUseException(message: String) : PosixException(EADDRINUSE, message)

    @ExperimentalIoApi
    public class NoSuchFileException(message: String) : PosixException(ENOENT, message)

    @ExperimentalIoApi
    public class OverflowException(message: String) : PosixException(EOVERFLOW, message)

    @ExperimentalIoApi
    public class NoMemoryException(message: String) : PosixException(ENOMEM, message)

    @ExperimentalIoApi
    public class PosixErrnoException(errno: Int, message: String) : PosixException(errno, "$message ($errno)")

    public companion object {
        /**
         * Create the corresponding instance of PosixException
         * with error message provided by the underlying POSIX implementation.
         *
         * @param errno error code returned by [posix.platform.errno]
         * @param posixFunctionName optional function name to be included to the exception message
         * @return an instance of [PosixException] or it's subtype
         */
        @ExperimentalIoApi
        public fun forErrno(errno: Int = platform.posix.errno, posixFunctionName: String? = null): PosixException = memScoped {
            val posixConstantName = KnownPosixErrors[errno]
            val posixErrorCodeMessage = when {
                posixConstantName == null -> "POSIX error $errno"
                else -> "$posixConstantName ($errno)"
            }

            val message = when {
                posixFunctionName.isNullOrBlank() -> posixErrorCodeMessage + ": " + strerror(errno)
                else -> "$posixFunctionName failed, $posixErrorCodeMessage: ${strerror(errno)}"
            }

            when (errno) {
                EBADF -> BadFileDescriptorException(message)

                // it is not guaranteed that these errors have identical numeric values
                // so we need to specify both
                @Suppress("DUPLICATE_LABEL_IN_WHEN")
                EWOULDBLOCK, EAGAIN -> TryAgainException(errno, message)

                EBADMSG -> BadMessageException(message)
                EINTR -> InterruptedException(message)
                EINVAL -> InvalidArgumentException(message)
                ECONNREFUSED -> ConnectionRefusedException(message)
                ECONNABORTED -> ConnectionAbortedException(message)
                ECONNRESET -> ConnectionResetException(message)
                ENOTCONN -> NotConnectedException(message)
                ETIMEDOUT -> TimeoutIOException(message)
                EOVERFLOW -> OverflowException(message)
                ENOMEM -> NoMemoryException(message)
                ENOTSOCK -> NotSocketException(message)
                EADDRINUSE -> AddressAlreadyInUseException(message)
                ENOENT -> NoSuchFileException(message)
                else -> PosixErrnoException(errno, message)
            }
        }
    }
}

internal fun PosixException.wrapIO(): IOException =
    IOException("I/O operation failed due to posix error code $errno", this)

private tailrec fun MemScope.strerror(errno: Int, size: size_t = 8192.convert()): String {
    val message = allocArray<ByteVar>(size.toLong())
    val result = strerror_r(errno, message, size)
    if (result == ERANGE) {
        return strerror(errno, size * 2.convert())
    }
    if (result != 0) {
        return "Unknown error ($errno)"
    }
    return message.toKString()
}
