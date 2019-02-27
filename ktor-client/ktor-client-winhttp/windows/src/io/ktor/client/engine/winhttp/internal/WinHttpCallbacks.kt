package io.ktor.client.engine.winhttp.internal

import kotlinx.cinterop.*
import platform.windows.DWORD
import platform.windows.DWORD_PTR
import platform.windows.LPVOID
import winhttp.*

fun statusCallback(
    @Suppress("UNUSED_PARAMETER") hInternet: HINTERNET?,
    dwContext: DWORD_PTR,
    dwStatus: DWORD,
    statusInfo: LPVOID?,
    statusInfoLength: DWORD
) {
    initRuntimeIfNeeded()

    val context = dwContext.toLong().toCPointer<COpaque>()?.asStableRef<WinHttpContext>()?.get()
    if (context?.isDisposed != false) {
        return
    }

    when (dwStatus) {
        WINHTTP_CALLBACK_STATUS_WRITE_COMPLETE.convert<UInt>() -> {
            context.onWriteDataComplete()
        }
        WINHTTP_CALLBACK_STATUS_SENDREQUEST_COMPLETE.convert<UInt>() -> {
            context.onSendRequestComplete()
        }
        WINHTTP_CALLBACK_STATUS_HEADERS_AVAILABLE.convert<UInt>() -> {
            context.onReceiveResponse()
        }
        WINHTTP_CALLBACK_STATUS_DATA_AVAILABLE.convert<UInt>() -> {
            val size = statusInfo!!.toLong().toCPointer<ULongVar>()!![0].convert<Long>()
            context.onQueryDataAvailable(size)
        }
        WINHTTP_CALLBACK_STATUS_READ_COMPLETE.convert<UInt>() -> {
            val size = statusInfoLength.convert<Int>()
            context.onReadComplete(size)
        }
        WINHTTP_CALLBACK_STATUS_REQUEST_ERROR.convert<UInt>() -> {
            val result = statusInfo!!.reinterpret<WINHTTP_ASYNC_RESULT>().pointed
            val errorMessage = getWinHttpErrorMessage(result.dwError)
            context.reject(errorMessage)
        }
        WINHTTP_CALLBACK_STATUS_SECURE_FAILURE.convert<UInt>() -> {
            val securityCode = statusInfo!!.reinterpret<UIntVar>().pointed.value
            val errorMessage = getWinHttpErrorMessage(securityCode)
            context.reject(errorMessage)
        }
    }
}
