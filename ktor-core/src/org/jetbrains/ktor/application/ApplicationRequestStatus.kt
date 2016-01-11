package org.jetbrains.ktor.application

// TODO: rename to ApplicationCallResult
public enum class ApplicationRequestStatus {
    Handled, Unhandled, Asynchronous
}