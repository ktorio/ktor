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
            val errorCode = "${result.dwError}"
            val callbackError = when (result.dwResult) {
                API_RECEIVE_RESPONSE.convert<ULong>() -> "The error occurred during a call to WinHttpReceiveResponse: $errorCode"
                API_QUERY_DATA_AVAILABLE.convert<ULong>() -> "The error occurred during a call to WinHttpQueryDataAvailable: $errorCode"
                API_READ_DATA.convert<ULong>() -> "The error occurred during a call to WinHttpReadData: $errorCode"
                API_WRITE_DATA.convert<ULong>() -> "The error occurred during a call to WinHttpWriteData: $errorCode"
                API_SEND_REQUEST.convert<ULong>() -> "The error occurred during a call to WinHttpSendRequest: $errorCode"
                else -> "Unknown status request error ${result.dwResult}: $errorCode"
            }
            context.reject(callbackError)
        }
        WINHTTP_CALLBACK_STATUS_SECURE_FAILURE.convert<UInt>() -> {
            val securityCode = statusInfo!!.reinterpret<UIntVar>().pointed.value
            val securityError = when (securityCode) {
                WINHTTP_CALLBACK_STATUS_FLAG_CERT_REV_FAILED.convert<UInt>() -> "Certification revocation check check failed"
                WINHTTP_CALLBACK_STATUS_FLAG_INVALID_CERT.convert<UInt>() -> "SSL certificate is invalid"
                WINHTTP_CALLBACK_STATUS_FLAG_CERT_REVOKED.convert<UInt>() -> "SSL certificate was revoked"
                WINHTTP_CALLBACK_STATUS_FLAG_INVALID_CA.convert<UInt>() -> "Invalid Certificate Authority"
                WINHTTP_CALLBACK_STATUS_FLAG_CERT_CN_INVALID.convert<UInt>() -> "SSL certificate common name is incorrect"
                WINHTTP_CALLBACK_STATUS_FLAG_CERT_DATE_INVALID.convert<UInt>() -> "SSL certificate is expired."
                WINHTTP_CALLBACK_STATUS_FLAG_SECURITY_CHANNEL_ERROR -> "Internal error while loading the SSL libraries"
                else -> "Unknown security error 0x${securityCode.toString(16)}"
            }
            context.reject(securityError)
        }
    }
}
