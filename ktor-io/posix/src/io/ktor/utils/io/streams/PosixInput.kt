package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.internal.utils.*
import platform.posix.*

/**
 * Create a blocking [Input] reading from the specified [fileDescriptor] using [read].
 */
@Suppress("FunctionName")
@ExperimentalIoApi
public fun Input(fileDescriptor: Int): Input = PosixInputForFileDescriptor(fileDescriptor)

/**
 * Create a blocking [Input] reading from the specified [file] instance using [fread].
 */
@Suppress("FunctionName")
@ExperimentalIoApi
public fun Input(file: CPointer<FILE>): Input = PosixInputForFile(file)

private class PosixInputForFileDescriptor(val fileDescriptor: Int) : AbstractInput() {
    private var closed = false
    init {
        check(fileDescriptor >= 0) { "Illegal fileDescriptor: $fileDescriptor" }
        check(kx_internal_is_non_blocking(fileDescriptor) == 0) { "File descriptor is in O_NONBLOCK mode." }
    }

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        val size = read(fileDescriptor, destination, offset, length)
        if (size < 0) {
            throw PosixException.forErrno(posixFunctionName = "read()").wrapIO()
        }

        return size
    }

    override fun closeSource() {
        if (closed) return
        closed = true

        if (close(fileDescriptor) != 0) {
            val error = errno
            if (error != EBADF) { // EBADF is already closed or not opened
                throw PosixException.forErrno(error, "close()").wrapIO()
            }
        }
    }
}

private class PosixInputForFile(val file: CPointer<FILE>) : AbstractInput() {
    private var closed = false

    override fun fill(destination: Memory, offset: Int, length: Int): Int {
        val size = fread(destination, offset, length, file)
        if (size == 0) {
            if (feof(file) != 0) return 0
            throw PosixException.forErrno(posixFunctionName = "read()").wrapIO()
        }

        return size
    }

    override fun closeSource() {
        if (closed) return
        closed = true

        if (fclose(file) != 0) {
            throw PosixException.forErrno(posixFunctionName = "fclose()").wrapIO()
        }
    }
}
