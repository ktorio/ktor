package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.winhttp.WinHttpIllegalStateException
import kotlinx.cinterop.*
import platform.windows.*
import winhttp.*

/**
 * Creates an exception from last WinHTTP error.
 */
internal fun createWinHttpError(message: String): WinHttpIllegalStateException {
    val errorCode = GetLastError()
    return WinHttpIllegalStateException("$message: ${getWinHttpErrorMessage(errorCode)}")
}

/**
 * Creates an error message from WinHTTP error.
 */
internal fun getWinHttpErrorMessage(errorCode: UInt): String {
    val moduleHandle = GetModuleHandleW("winhttp.dll")
    val flags = FORMAT_MESSAGE_FROM_HMODULE or
        FORMAT_MESSAGE_IGNORE_INSERTS or
        FORMAT_MESSAGE_ARGUMENT_ARRAY

    return run {
        var buffer = ShortArray(256)
        var length = buffer.usePinned {
            FormatMessageW(
                flags.convert(),
                moduleHandle,
                errorCode,
                0,
                it.addressOf(0).reinterpret(),
                buffer.size.convert(),
                null
            ).toInt()
        }

        if (length > 0) {
            getTrimmedString(buffer, length)
        } else {
            if (GetLastError() == ERROR_INSUFFICIENT_BUFFER.convert<UInt>()) {
                memScoped {
                    val bufferPtr = alloc<IntVar>()
                    length = FormatMessageW(
                        (flags or FORMAT_MESSAGE_ALLOCATE_BUFFER).convert(),
                        moduleHandle,
                        errorCode,
                        0,
                        bufferPtr.ptr.reinterpret(),
                        0,
                        null
                    ).toInt()

                    val array = bufferPtr.value.toLong().toCPointer<ShortVar>()!!
                    buffer = ShortArray(length) {
                        array[it]
                    }
                    nativeHeap.free(array)
                }

                getTrimmedString(buffer, length)
            }
            "Unknown error"
        }
    } + " (0x${GetHResultFromError(errorCode).toString(16)})"
}

private fun getTrimmedString(buffer: ShortArray, size: Int): String {
    var length = size
    while (length > 0 && buffer[length - 1] <= 32) {
        length--
    }

    return String(CharArray(length) {
        buffer[it].toChar()
    })
}
